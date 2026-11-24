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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * Unit test for {@link PlatformDependent}.
 */
public class PlatformDependentTest {

    @Test
    public void testCreateTempFileIsOnlyReadableByOwner() throws Exception {
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", null);
        try {
            assertTrue(file.isFile());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertTrue(file.canRead());
            assertTrue(file.canWrite());
            assertOnlyReadableByOwner(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileInDirectoryIsOnlyReadableByOwner() throws Exception {
        File dir = newTempDirectory();
        File file = null;
        try {
            file = PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
            assertTrue(file.isFile());
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertOnlyReadableByOwner(file);
        } finally {
            if (file != null) {
                file.delete();
            }
            dir.delete();
        }
    }

    @Test
    public void testCreateTempFileLegacyIsOnlyReadableByOwner() throws Exception {
        // The fallback used before Java 7 must restrict the permissions as well.
        File file = PlatformDependent.createTempFileLegacy("netty-test-", ".tmp", null);
        try {
            assertTrue(file.isFile());
            assertTrue(file.canRead());
            assertOnlyReadableByOwner(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileLegacyInDirectoryIsOnlyReadableByOwner() throws Exception {
        File dir = newTempDirectory();
        File file = null;
        try {
            file = PlatformDependent.createTempFileLegacy("netty-test-", ".tmp", dir);
            assertTrue(file.isFile());
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertOnlyReadableByOwner(file);
        } finally {
            if (file != null) {
                file.delete();
            }
            dir.delete();
        }
    }

    @Test(expected = IOException.class)
    public void testCreateTempFileInMissingDirectory() throws Exception {
        File dir = newTempDirectory();
        assertTrue(dir.delete());
        PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
    }

    private static File newTempDirectory() throws IOException {
        File dir = File.createTempFile("netty-test-dir-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        return dir;
    }

    /**
     * Asserts that the given file is readable by its owner only. The test is
     * skipped when the POSIX file permissions can not be read (Java 6 or
     * earlier, or a non-POSIX file system).
     */
    public static void assertOnlyReadableByOwner(File file) throws Exception {
        Set<String> permissions = posixFilePermissions(file);
        assumeNotNull(permissions);
        assertTrue("owner must be able to read " + file + ": " + permissions,
                permissions.contains("OWNER_READ"));
        assertFalse("group must not be able to read " + file + ": " + permissions,
                permissions.contains("GROUP_READ"));
        assertFalse("others must not be able to read " + file + ": " + permissions,
                permissions.contains("OTHERS_READ"));
    }

    /**
     * Returns the names of the POSIX file permissions of the given file, or
     * {@code null} if they are not available.
     */
    private static Set<String> posixFilePermissions(File file) throws Exception {
        final Class<?> filesClass;
        try {
            filesClass = Class.forName("java.nio.file.Files");
        } catch (ClassNotFoundException e) {
            // Java 6 or earlier
            return null;
        }
        Class<?> pathClass = Class.forName("java.nio.file.Path");
        Object noLinkOptions = Array.newInstance(Class.forName("java.nio.file.LinkOption"), 0);
        Method getPosixFilePermissions = filesClass.getMethod(
                "getPosixFilePermissions", pathClass, noLinkOptions.getClass());
        Object path = File.class.getMethod("toPath").invoke(file);

        Set<?> permissions;
        try {
            permissions = (Set<?>) getPosixFilePermissions.invoke(null, path, noLinkOptions);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                // Not a POSIX file system
                return null;
            }
            throw e;
        }

        Set<String> names = new HashSet<String>();
        for (Object permission: permissions) {
            names.add(String.valueOf(permission));
        }
        return names;
    }
}
