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
package org.jboss.netty.handler.codec.http.multipart;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;

import static org.jboss.netty.util.internal.PlatformDependentTest.*;
import static org.junit.Assert.*;

/**
 * The temporary files of {@link DiskFileUpload} and {@link DiskAttribute} hold
 * the (possibly sensitive) uploaded data and must not be readable by other local
 * users (CVE-2021-21290).
 */
public class DiskFileUploadTest {

    private static final String SECRET = "secret uploaded content";

    @Test
    public void testSetContentTempFileIsOnlyReadableByOwner() throws Exception {
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = null;
        DiskFileUpload upload = newUpload();
        try {
            upload.setContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertNotNull(file);
            assertTrue(file.getName().startsWith(DiskFileUpload.prefix));
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
            assertOnlyReadableByOwner(file);
        } finally {
            upload.delete();
            DiskFileUpload.baseDirectory = oldBaseDirectory;
        }
    }

    @Test
    public void testAddContentTempFileIsOnlyReadableByOwner() throws Exception {
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = null;
        DiskFileUpload upload = newUpload();
        try {
            upload.addContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8), false);
            File file = upload.getFile();
            assertNotNull(file);
            assertOnlyReadableByOwner(file);
            upload.addContent(ChannelBuffers.EMPTY_BUFFER, true);
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
        } finally {
            upload.delete();
            DiskFileUpload.baseDirectory = oldBaseDirectory;
        }
    }

    @Test
    public void testSetContentFromStreamTempFileIsOnlyReadableByOwner() throws Exception {
        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = null;
        DiskFileUpload upload = newUpload();
        File file = null;
        try {
            upload.setContent(new ByteArrayInputStream(SECRET.getBytes("UTF-8")));
            file = upload.getFile();
            assertNotNull(file);
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
            assertOnlyReadableByOwner(file);
        } finally {
            upload.delete();
            if (file != null) {
                file.delete();
            }
            DiskFileUpload.baseDirectory = oldBaseDirectory;
        }
    }

    @Test
    public void testBaseDirectoryTempFileIsOnlyReadableByOwner() throws Exception {
        File dir = File.createTempFile("netty-upload-dir-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());

        String oldBaseDirectory = DiskFileUpload.baseDirectory;
        DiskFileUpload.baseDirectory = dir.getAbsolutePath();
        DiskFileUpload upload = newUpload();
        try {
            upload.setContent(ChannelBuffers.copiedBuffer(SECRET, CharsetUtil.UTF_8));
            File file = upload.getFile();
            assertNotNull(file);
            assertEquals(dir.getCanonicalFile(), file.getParentFile().getCanonicalFile());
            assertEquals(SECRET, upload.getString(CharsetUtil.UTF_8));
            assertOnlyReadableByOwner(file);
        } finally {
            upload.delete();
            DiskFileUpload.baseDirectory = oldBaseDirectory;
            dir.delete();
        }
    }

    @Test
    public void testDiskAttributeTempFileIsOnlyReadableByOwner() throws Exception {
        String oldBaseDirectory = DiskAttribute.baseDirectory;
        DiskAttribute.baseDirectory = null;
        DiskAttribute attribute = new DiskAttribute("password");
        try {
            attribute.setValue(SECRET);
            File file = attribute.getFile();
            assertNotNull(file);
            assertTrue(file.getName().startsWith(DiskAttribute.prefix));
            assertEquals(SECRET, attribute.getValue());
            assertOnlyReadableByOwner(file);
        } finally {
            attribute.delete();
            DiskAttribute.baseDirectory = oldBaseDirectory;
        }
    }

    private static DiskFileUpload newUpload() {
        return new DiskFileUpload("file", "secret.txt", "text/plain", null, CharsetUtil.UTF_8, 0);
    }
}
