/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class HttpMessageDecoderTest {

    @Test
    public void testHeaderWithNoValueAndMissingColon() {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Host:\r\n" +
                "netty.io\r\n\r\n";

        try {
            embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testMultipleContentLengthHeaders() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1,1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersWithFolding() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Content-Length:\r\n" +
                "\t6\r\n\r\n" +
                "123456";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthAndTransferEncodingChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithPositiveSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: +1\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeSign() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -1\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithNegativeValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: -10\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValue() {
        // When allowDuplicateContentLengths is false (default), even identical values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0\r\n" +
                "Content-Length: 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersSameValueWithComma() {
        // When allowDuplicateContentLengths is false (default), even identical comma-separated values should be rejected
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 0, 0\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    // ---------------------------------------------------------------------
    // GHSA-wx5j-54mm-rqqq: header names must be bracketed by OWS only.
    // The decoder used to silently strip every Character.isWhitespace char,
    // which let an attacker smuggle control characters around a header name.
    // The strict OWS check now rejects 0x0c (FF) and 0x1c-0x1f (FS/GS/RS/US)
    // both before the name and between the name and the colon.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestHeaderNameStartsWithControlChar1c() {
        testRequestHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1d() {
        testRequestHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1e() {
        testRequestHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar1f() {
        testRequestHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameStartsWithControlChar0c() {
        testRequestHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1c() {
        testRequestHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1d() {
        testRequestHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1e() {
        testRequestHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar1f() {
        testRequestHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testRequestHeaderNameEndsWithControlChar0c() {
        testRequestHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testRequestHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer requestBuffer = ChannelBuffers.dynamicBuffer();
        requestBuffer.writeBytes("GET /some/path HTTP/1.1\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidRequestHeaders0(requestBuffer);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1c() {
        testResponseHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1d() {
        testResponseHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1e() {
        testResponseHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar1f() {
        testResponseHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameStartsWithControlChar0c() {
        testResponseHeaderNameStartsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameStartsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes("Transfer-Encoding: chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1c() {
        testResponseHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1d() {
        testResponseHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1e() {
        testResponseHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar1f() {
        testResponseHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testResponseHeaderNameEndsWithControlChar0c() {
        testResponseHeaderNameEndsWithControlChar(0x0c);
    }

    private static void testResponseHeaderNameEndsWithControlChar(int controlChar) {
        ChannelBuffer responseBuffer = ChannelBuffers.dynamicBuffer();
        responseBuffer.writeBytes("HTTP/1.1 200 OK\r\nHost: netty.io\r\n".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeBytes("Transfer-Encoding".getBytes(CharsetUtil.US_ASCII));
        responseBuffer.writeByte(controlChar);
        responseBuffer.writeBytes(": chunked\r\n\r\n".getBytes(CharsetUtil.US_ASCII));
        testInvalidResponseHeaders0(responseBuffer);
    }

    // ---------------------------------------------------------------------
    // CVE-2019-16869: no whitespace is allowed between a header field-name and
    // the colon (RFC 7230 section 3.2.4). For requests this must be rejected
    // (400 Bad Request); for responses the whitespace is silently skipped so
    // that interop with non-compliant servers is preserved.
    // Backport of netty/netty@39cafcb (issue #9571 / PR #9585).
    // ---------------------------------------------------------------------

    @Test
    public void testWhitespaceInRequestHeaderName() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    @Test
    public void testWhitespaceInResponseHeaderName() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\r\n\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        embedder.offer(ChannelBuffers.copiedBuffer(responseStr, CharsetUtil.US_ASCII));
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpHeaders.Values.CHUNKED,
                response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING));
        assertEquals("netty.io", response.getHeader(HttpHeaders.Names.HOST));
    }

    // ---------------------------------------------------------------------
    // CVE-2026-93566: the chunk-size line must be validated strictly. The
    // decoder used to trim the line and truncate it at the first ';',
    // whitespace or control character (and to drop a bare CR together with
    // the byte after it), so lines such as "5 c", "5\0" or "5\rX" were read
    // as chunk size 5. A front-end that reads such a line differently can
    // then be used to smuggle a request. Backport of netty/netty@161f9ad.
    // ---------------------------------------------------------------------

    @Test
    public void testRequestChunkSizeEmpty() {
        testInvalidRequestChunkSize("");
    }

    @Test
    public void testRequestChunkSizeWithTrailingWhitespace() {
        testInvalidRequestChunkSize("5 ");
    }

    @Test
    public void testRequestChunkSizeWithLeadingWhitespace() {
        testInvalidRequestChunkSize(" 5");
    }

    @Test
    public void testRequestChunkSizeWithWhitespaceAndHexDigit() {
        testInvalidRequestChunkSize("5 c");
    }

    @Test
    public void testRequestChunkSizeWithWhitespaceAndExtraToken() {
        testInvalidRequestChunkSize("5 x");
    }

    @Test
    public void testRequestChunkSizeWithWhitespaceAndHexDigitAndExtensions() {
        testInvalidRequestChunkSize("5 c;foo=bar");
    }

    @Test
    public void testRequestChunkSizeWithLeadingWhitespaceAndHexDigit() {
        testInvalidRequestChunkSize("  5 c");
    }

    @Test
    public void testRequestChunkSizeWithLeadingWhitespaceAndExtraToken() {
        testInvalidRequestChunkSize("  5 x");
    }

    @Test
    public void testRequestChunkSizeWithLeadingWhitespaceAndHexDigitAndExtensions() {
        testInvalidRequestChunkSize("  5 c;foo=bar");
    }

    @Test
    public void testRequestChunkSizeWithTrailingNul() {
        testInvalidRequestChunkSize("5\u0000");
    }

    @Test
    public void testRequestChunkSizeWithNulAndHexDigit() {
        testInvalidRequestChunkSize("5\u0000c");
    }

    @Test
    public void testRequestChunkSizeWithLeadingNul() {
        testInvalidRequestChunkSize("\u00005");
    }

    @Test
    public void testRequestChunkSizeWithTrailingControlChar01() {
        testInvalidRequestChunkSize("5\u0001");
    }

    @Test
    public void testRequestChunkSizeWithTrailingVerticalTab() {
        testInvalidRequestChunkSize("5\u000b");
    }

    @Test
    public void testRequestChunkSizeWithTrailingFormFeed() {
        testInvalidRequestChunkSize("5\u000c");
    }

    @Test
    public void testRequestChunkSizeWithTrailingControlChar1f() {
        testInvalidRequestChunkSize("5\u001f");
    }

    @Test
    public void testRequestChunkSizeWithTrailingDelete() {
        testInvalidRequestChunkSize("5\u007f");
    }

    @Test
    public void testRequestChunkSizeWithTrailingTab() {
        testInvalidRequestChunkSize("5\t");
    }

    @Test
    public void testRequestChunkSizeWithTabAndHexDigit() {
        testInvalidRequestChunkSize("5\tc");
    }

    @Test
    public void testRequestChunkSizeWithBareCarriageReturn() {
        testInvalidRequestChunkSize("5\rX");
    }

    @Test
    public void testRequestChunkSizeWithBareCarriageReturnAndHexDigit() {
        testInvalidRequestChunkSize("5\rc");
    }

    @Test
    public void testRequestChunkSizeWithDoubleCarriageReturn() {
        testInvalidRequestChunkSize("5\r");
    }

    @Test
    public void testRequestChunkSizeWithPlusSign() {
        testInvalidRequestChunkSize("+5");
    }

    @Test
    public void testResponseChunkSizeEmpty() {
        testInvalidResponseChunkSize("");
    }

    @Test
    public void testResponseChunkSizeWithTrailingWhitespace() {
        testInvalidResponseChunkSize("5 ");
    }

    @Test
    public void testResponseChunkSizeWithLeadingWhitespace() {
        testInvalidResponseChunkSize(" 5");
    }

    @Test
    public void testResponseChunkSizeWithWhitespaceAndHexDigit() {
        testInvalidResponseChunkSize("5 c");
    }

    @Test
    public void testResponseChunkSizeWithWhitespaceAndExtraToken() {
        testInvalidResponseChunkSize("5 x");
    }

    @Test
    public void testResponseChunkSizeWithWhitespaceAndHexDigitAndExtensions() {
        testInvalidResponseChunkSize("5 c;foo=bar");
    }

    @Test
    public void testResponseChunkSizeWithLeadingWhitespaceAndHexDigit() {
        testInvalidResponseChunkSize("  5 c");
    }

    @Test
    public void testResponseChunkSizeWithLeadingWhitespaceAndExtraToken() {
        testInvalidResponseChunkSize("  5 x");
    }

    @Test
    public void testResponseChunkSizeWithLeadingWhitespaceAndHexDigitAndExtensions() {
        testInvalidResponseChunkSize("  5 c;foo=bar");
    }

    @Test
    public void testResponseChunkSizeWithTrailingNul() {
        testInvalidResponseChunkSize("5\u0000");
    }

    @Test
    public void testResponseChunkSizeWithTrailingVerticalTab() {
        testInvalidResponseChunkSize("5\u000b");
    }

    @Test
    public void testResponseChunkSizeWithTrailingControlChar01() {
        testInvalidResponseChunkSize("5\u0001");
    }

    @Test
    public void testResponseChunkSizeWithBareCarriageReturn() {
        testInvalidResponseChunkSize("5\rX");
    }

    @Test
    public void testResponseChunkSizeWithPlusSign() {
        testInvalidResponseChunkSize("+5");
    }

    @Test
    public void testRequestChunkSizeValid() {
        testValidRequestChunkSize("5", "hello");
    }

    @Test
    public void testRequestChunkSizeWithLeadingZeros() {
        testValidRequestChunkSize("0005", "hello");
    }

    @Test
    public void testRequestChunkSizeUpperAndLowerCaseHex() {
        testValidRequestChunkSize("B", "hello world");
        testValidRequestChunkSize("b", "hello world");
    }

    @Test
    public void testRequestChunkSizeWithExtensions() {
        testValidRequestChunkSize("5;foo=bar", "hello");
    }

    @Test
    public void testRequestChunkSizeWithBadWhitespaceBeforeExtensions() {
        testValidRequestChunkSize("5 ;foo=bar", "hello");
        testValidRequestChunkSize("5\t;foo=bar", "hello");
        testValidRequestChunkSize("5 \t ;foo=bar", "hello");
    }

    @Test
    public void testRequestChunkSizeOverflow() {
        testInvalidRequestChunkSize("80000000");
    }

    private static void testInvalidRequestChunkSize(String chunkSizeLine) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                "GPOST\r\n" +
                "0\r\n" +
                "\r\n" +
                "GET /smuggled HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        assertChunkSizeRejected(embedder, requestStr);

        // The request headers parse fine.
        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertEquals(HttpVersion.HTTP_1_1, request.getProtocolVersion());
        assertEquals(HttpMethod.POST, request.getMethod());
        assertTrue(request.isChunked());

        assertNoSmuggledMessage(embedder);
    }

    private static void testInvalidResponseChunkSize(String chunkSizeLine) {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                "GPOST\r\n" +
                "0\r\n" +
                "\r\n" +
                "HTTP/1.1 200 smuggled\r\n" +
                "Host: example\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        assertChunkSizeRejected(embedder, responseStr);

        // The response headers parse fine.
        HttpResponse response = (HttpResponse) embedder.poll();
        assertNotNull(response);
        assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        assertEquals(HttpResponseStatus.OK, response.getStatus());
        assertTrue(response.isChunked());

        assertNoSmuggledMessage(embedder);
    }

    private static void assertChunkSizeRejected(DecoderEmbedder<Object> embedder, String messageStr) {
        // ISO-8859-1 maps every char of the test strings to the very same byte.
        try {
            embedder.offer(ChannelBuffers.copiedBuffer(messageStr, CharsetUtil.ISO_8859_1));
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            // The malformed chunk-size line must be rejected, not truncated.
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof NumberFormatException);
            String message = e.getCause().getMessage().toLowerCase(Locale.ENGLISH);
            assertTrue(message, message.contains("chunk size"));
        }
    }

    private static void assertNoSmuggledMessage(DecoderEmbedder<Object> embedder) {
        // Neither the chunk nor anything after it may be surfaced, in particular not a
        // second, smuggled message.
        assertNull(embedder.poll());
        try {
            embedder.finish();
        } catch (CodecEmbedderException e) {
            // The pending malformed chunk-size line is rejected again when the channel closes.
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof NumberFormatException);
        }
        assertNull(embedder.poll());
    }

    private static void testValidRequestChunkSize(String chunkSizeLine, String data) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                chunkSizeLine + "\r\n" +
                data + "\r\n" +
                "0\r\n" +
                "\r\n";
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        embedder.offer(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.ISO_8859_1));

        HttpRequest request = (HttpRequest) embedder.poll();
        assertNotNull(request);
        assertEquals(HttpMethod.POST, request.getMethod());
        assertTrue(request.isChunked());

        HttpChunk chunk = (HttpChunk) embedder.poll();
        assertNotNull(chunk);
        assertFalse(chunk.isLast());
        assertEquals(data, chunk.getContent().toString(CharsetUtil.ISO_8859_1));

        HttpChunk lastChunk = (HttpChunk) embedder.poll();
        assertNotNull(lastChunk);
        assertTrue(lastChunk.isLast());

        assertNull(embedder.poll());
    }

    private static void testInvalidHeaders0(String requestStr) {
        testInvalidRequestHeaders0(ChannelBuffers.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    private static void testInvalidRequestHeaders0(ChannelBuffer requestBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpRequestDecoder());
        try {
            embedder.offer(requestBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }

    private static void testInvalidResponseHeaders0(ChannelBuffer responseBuffer) {
        DecoderEmbedder<Object> embedder = new DecoderEmbedder<Object>(new HttpResponseDecoder());
        try {
            embedder.offer(responseBuffer);
            fail("Expected CodecEmbedderException");
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
        }
    }
}
