/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility that provides operations whose implementation depends on the
 * current runtime environment, such as the Java version.
 * <p>
 * APIs which are not part of Java 5 are looked up and invoked reflectively.
 */
public final class PlatformDependent {

    // java.nio.file.Files.createTempFile(String, String, FileAttribute...) (Java 7+)
    private static final Method FILES_CREATE_TEMP_FILE;
    // java.nio.file.Files.createTempFile(Path, String, String, FileAttribute...) (Java 7+)
    private static final Method FILES_CREATE_TEMP_FILE_IN_DIRECTORY;
    // java.io.File.toPath() (Java 7+)
    private static final Method FILE_TO_PATH;
    // java.nio.file.Path.toFile() (Java 7+)
    private static final Method PATH_TO_FILE;
    // empty java.nio.file.attribute.FileAttribute[] (Java 7+)
    private static final Object NO_FILE_ATTRIBUTES;
    // java.io.File.setReadable(boolean, boolean) (Java 6+)
    private static final Method FILE_SET_READABLE;

    static {
        Method createTempFile = null;
        Method createTempFileInDirectory = null;
        Method toPath = null;
        Method toFile = null;
        Object noFileAttributes = null;
        try {
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> fileAttributeClass = Class.forName("java.nio.file.attribute.FileAttribute");
            Object attributes = Array.newInstance(fileAttributeClass, 0);
            Method m1 = filesClass.getMethod(
                    "createTempFile", String.class, String.class, attributes.getClass());
            Method m2 = filesClass.getMethod(
                    "createTempFile", pathClass, String.class, String.class, attributes.getClass());
            Method m3 = File.class.getMethod("toPath");
            Method m4 = pathClass.getMethod("toFile");

            createTempFile = m1;
            createTempFileInDirectory = m2;
            toPath = m3;
            toFile = m4;
            noFileAttributes = attributes;
        } catch (Throwable t) {
            // Java 6 or earlier - NIO.2 is not available.
        }
        FILES_CREATE_TEMP_FILE = createTempFile;
        FILES_CREATE_TEMP_FILE_IN_DIRECTORY = createTempFileInDirectory;
        FILE_TO_PATH = toPath;
        PATH_TO_FILE = toFile;
        NO_FILE_ATTRIBUTES = noFileAttributes;

        Method setReadable = null;
        try {
            setReadable = File.class.getMethod("setReadable", boolean.class, boolean.class);
        } catch (Throwable t) {
            // Java 5 - there is no API to adjust the file permissions.
        }
        FILE_SET_READABLE = setReadable;
    }

    /**
     * Creates a new temporary file which is only readable by its owner,
     * unlike {@link File#createTempFile(String, String, File)} which creates
     * the file with the default (usually world-readable) permissions.
     * <p>
     * On Java 7+ {@code java.nio.file.Files#createTempFile} is used. On older
     * Java versions the file is created via {@link File#createTempFile(String, String, File)}
     * and its read permission is restricted to the owner afterwards (Java 5
     * provides no API to adjust the permissions, so they are left unchanged).
     *
     * @param prefix    the prefix string of the file name
     * @param suffix    the suffix string of the file name, may be {@code null}
     * @param directory the directory in which the file is to be created,
     *                  or {@code null} to use the default temporary-file directory
     * @return the newly created temporary file
     * @throws IOException if the file could not be created or its permissions
     *                     could not be adjusted
     *
     * @see <a href="https://github.com/netty/netty/security/advisories/GHSA-5mcr-gq6c-3hq2">
     *      CVE-2021-21290: Local information disclosure via the system temporary directory</a>
     */
    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (DetectionUtil.javaVersion() >= 7 && FILES_CREATE_TEMP_FILE != null) {
            File file = createTempFileNio(prefix, suffix, directory);
            if (file != null) {
                return file;
            }
        }
        return createTempFileLegacy(prefix, suffix, directory);
    }

    /**
     * Creates the temporary file via {@code java.nio.file.Files#createTempFile},
     * which uses owner-only permissions. Returns {@code null} if the NIO.2 API
     * could not be invoked.
     */
    private static File createTempFileNio(String prefix, String suffix, File directory) throws IOException {
        try {
            Object path;
            if (directory == null) {
                path = FILES_CREATE_TEMP_FILE.invoke(null, prefix, suffix, NO_FILE_ATTRIBUTES);
            } else {
                Object directoryPath = FILE_TO_PATH.invoke(directory);
                path = FILES_CREATE_TEMP_FILE_IN_DIRECTORY.invoke(
                        null, directoryPath, prefix, suffix, NO_FILE_ATTRIBUTES);
            }
            return (File) PATH_TO_FILE.invoke(path);
        } catch (InvocationTargetException e) {
            throw rethrow(e);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Creates the temporary file via {@link File#createTempFile(String, String, File)}
     * and restricts its read permission to the owner.
     */
    static File createTempFileLegacy(String prefix, String suffix, File directory) throws IOException {
        final File file;
        if (directory == null) {
            file = File.createTempFile(prefix, suffix);
        } else {
            file = File.createTempFile(prefix, suffix, directory);
        }

        // Try to adjust the perms, if this fails there is not much else we can do...
        if (!setReadable(file, false, false)) {
            throw new IOException("Failed to set permissions on temporary file " + file);
        }
        if (!setReadable(file, true, true)) {
            throw new IOException("Failed to set permissions on temporary file " + file);
        }
        return file;
    }

    /**
     * Invokes {@link File#setReadable(boolean, boolean)} if available.
     * Returns {@code true} on Java 5 where the permissions can not be adjusted.
     */
    private static boolean setReadable(File file, boolean readable, boolean ownerOnly) throws IOException {
        if (FILE_SET_READABLE == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(FILE_SET_READABLE.invoke(file, readable, ownerOnly));
        } catch (InvocationTargetException e) {
            throw rethrow(e);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static IOException rethrow(InvocationTargetException e) throws IOException {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
            throw (IOException) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        IOException ioe = new IOException(String.valueOf(cause));
        ioe.initCause(cause);
        throw ioe;
    }

    private PlatformDependent() {
        // only static method supported
    }
}
