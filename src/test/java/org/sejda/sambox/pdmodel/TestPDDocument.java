/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sejda.sambox.pdmodel;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.sejda.io.SeekableSources;
import org.sejda.sambox.input.PDFParser;
import org.sejda.sambox.util.SpecVersionUtils;

import junit.framework.TestCase;

/**
 * Testcase introduced with PDFBOX-1581.
 * 
 */
public class TestPDDocument extends TestCase
{
    private File testResultsDir = new File("target/test-output");

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        testResultsDir.mkdirs();
    }

    /**
     * Test document save/load using a stream.
     * 
     * @throws IOException if something went wrong
     */
    public void testSaveLoadStream() throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Create PDF with one blank page
        try (PDDocument document = new PDDocument())
        {
            document.addPage(new PDPage());
            document.writeTo(baos);
        }

        // Verify content
        byte[] pdf = baos.toByteArray();
        assertTrue(pdf.length > 200);
        assertEquals("%PDF-1.4", new String(Arrays.copyOfRange(pdf, 0, 8), "UTF-8"));
        assertEquals("%%EOF\n",
                new String(Arrays.copyOfRange(pdf, pdf.length - 6, pdf.length), "UTF-8"));

        // Load
        try (PDDocument loadDoc = PDFParser.parse(SeekableSources.inMemorySeekableSourceFrom(pdf)))
        {
            assertEquals(1, loadDoc.getNumberOfPages());
        }
    }

    /**
     * Test document save/load using a file.
     * 
     * @throws IOException if something went wrong
     */
    public void testSaveLoadFile() throws IOException
    {
        File targetFile = new File(testResultsDir, "pddocument-saveloadfile.pdf");
        // Create PDF with one blank page
        try (PDDocument document = new PDDocument())
        {
            document.addPage(new PDPage());
            document.writeTo(targetFile);
        }

        // Verify content
        assertTrue(targetFile.length() > 200);
        InputStream in = new FileInputStream(targetFile);
        byte[] pdf = IOUtils.toByteArray(in);
        in.close();
        assertTrue(pdf.length > 200);
        assertEquals("%PDF-1.4", new String(Arrays.copyOfRange(pdf, 0, 8), "UTF-8"));
        assertEquals("%%EOF\n",
                new String(Arrays.copyOfRange(pdf, pdf.length - 6, pdf.length), "UTF-8"));

        // Load
        try (PDDocument loadDoc = PDFParser.parse(SeekableSources.inMemorySeekableSourceFrom(pdf)))
        {
            assertEquals(1, loadDoc.getNumberOfPages());
        }
    }

    /**
     * PDFBOX-3481: Test whether XRef generation results in unusable PDFs if Arab numbering is default.
     */
    public void testSaveArabicLocale() throws IOException
    {
        Locale defaultLocale = Locale.getDefault();
        try
        {
            Locale arabicLocale = new Locale.Builder().setLanguageTag("ar-EG-u-nu-arab").build();
            Locale.setDefault(arabicLocale);

            File targetFile = new File(testResultsDir, "pddocument-savearabicfile.pdf");

            // Create PDF with one blank page
            try (PDDocument document = new PDDocument())
            {
                document.addPage(new PDPage());
                document.writeTo(targetFile);
            }

            // Load
            try (PDDocument loadDoc = PDFParser
                    .parse(SeekableSources.seekableSourceFrom(targetFile)))
            {
                assertEquals(1, loadDoc.getNumberOfPages());
            }
        }
        finally
        {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    public void defaultVersion() throws IOException
    {
        try (PDDocument document = new PDDocument())
        {
            // test default version
            assertEquals(SpecVersionUtils.V1_4, document.getVersion());
            assertEquals(SpecVersionUtils.V1_4, document.getDocument().getHeaderVersion());
            assertEquals(SpecVersionUtils.V1_4, document.getDocumentCatalog().getVersion());
        }
    }

    @Test
    public void downgradeVersion() throws IOException
    {
        try (PDDocument document = new PDDocument())
        {
            document.getDocument().setHeaderVersion(SpecVersionUtils.V1_3);
            document.getDocumentCatalog().setVersion(null);
            assertEquals(SpecVersionUtils.V1_3, document.getVersion());
            assertEquals(SpecVersionUtils.V1_3, document.getDocument().getHeaderVersion());
            assertNull(document.getDocumentCatalog().getVersion());
        }
    }

    @Test
    public void cannotDowngradeVersion() throws IOException
    {
        try (PDDocument document = new PDDocument())
        {
            document.setVersion(SpecVersionUtils.V1_3);
            assertEquals(SpecVersionUtils.V1_4, document.getVersion());
            assertEquals(SpecVersionUtils.V1_4, document.getDocument().getHeaderVersion());
            assertEquals(SpecVersionUtils.V1_4, document.getDocumentCatalog().getVersion());
        }
    }

    @Test
    public void versionUpgrade() throws IOException
    {
        try (PDDocument document = new PDDocument())
        {
            document.setVersion(SpecVersionUtils.V1_5);
            assertEquals(SpecVersionUtils.V1_5, document.getVersion());
            assertEquals(SpecVersionUtils.V1_4, document.getDocument().getHeaderVersion());
            assertEquals(SpecVersionUtils.V1_5, document.getDocumentCatalog().getVersion());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requiredNotBlankVersion()
    {
        new PDDocument().getDocument().setHeaderVersion(" ");
    }
}
