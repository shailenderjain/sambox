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
package org.apache.pdfbox.pdmodel.font;

import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.afm.AFMParser;
import org.apache.fontbox.afm.FontMetrics;
import org.apache.fontbox.ttf.Type1Equivalent;
import org.apache.fontbox.type1.Type1Font;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.encoding.Encoding;
import org.apache.pdfbox.encoding.StandardEncoding;
import org.apache.pdfbox.encoding.Type1Encoding;
import org.apache.pdfbox.encoding.WinAnsiEncoding;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;

/**
 * A PostScript Type 1 Font.
 *
 * @author Ben Litchfield
 */
public class PDType1Font extends PDSimpleFont implements PDType1Equivalent
{
    private static final Log LOG = LogFactory.getLog(PDType1Font.class);

    /**
     * The static map of the default Adobe font metrics.
     */
    private static final Map<String, FontMetrics> AFM_MAP;
    static
    {
        try
        {
            AFM_MAP = new HashMap<String, FontMetrics>();
            addMetric("Courier-Bold");
            addMetric("Courier-BoldOblique");
            addMetric("Courier");
            addMetric("Courier-Oblique");
            addMetric("Helvetica");
            addMetric("Helvetica-Bold");
            addMetric("Helvetica-BoldOblique");
            addMetric("Helvetica-Oblique");
            addMetric("Symbol");
            addMetric("Times-Bold");
            addMetric("Times-BoldItalic");
            addMetric("Times-Italic");
            addMetric("Times-Roman");
            addMetric("ZapfDingbats");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void addMetric(String fontName) throws IOException
    {
        String resourceName = "org/apache/pdfbox/resources/afm/" + fontName + ".afm";
        URL url = PDType1Font.class.getClassLoader().getResource(resourceName);
        if (url != null)
        {
            InputStream afmStream = url.openStream();
            try
            {
                AFMParser parser = new AFMParser(afmStream);
                FontMetrics metric = parser.parse();
                AFM_MAP.put(fontName, metric);
            }
            finally
            {
                afmStream.close();
            }
        }
        else
        {
            throw new IOException(resourceName + " not found");
        }
    }

    // alternative names for glyphs which are commonly encountered
    private static final Map<String, String> ALT_NAMES = new HashMap<String, String>();
    static
    {
        ALT_NAMES.put("ff", "f_f");
        ALT_NAMES.put("ffi", "f_f_i");
        ALT_NAMES.put("ffl", "f_f_l");
        ALT_NAMES.put("fi", "f_i");
        ALT_NAMES.put("fl", "f_l");
        ALT_NAMES.put("st", "s_t");
        ALT_NAMES.put("IJ", "I_J");
        ALT_NAMES.put("ij", "i_j");
        ALT_NAMES.put("ellipsis", "elipsis"); // misspelled in ArialMT
    }

    // todo: replace with enum? or getters?
    public static final PDType1Font TIMES_ROMAN = new PDType1Font("Times-Roman");
    public static final PDType1Font TIMES_BOLD = new PDType1Font("Times-Bold");
    public static final PDType1Font TIMES_ITALIC = new PDType1Font("Times-Italic");
    public static final PDType1Font TIMES_BOLD_ITALIC = new PDType1Font("Times-BoldItalic");
    public static final PDType1Font HELVETICA = new PDType1Font("Helvetica");
    public static final PDType1Font HELVETICA_BOLD = new PDType1Font("Helvetica-Bold");
    public static final PDType1Font HELVETICA_OBLIQUE = new PDType1Font("Helvetica-Oblique");
    public static final PDType1Font HELVETICA_BOLD_OBLIQUE = new PDType1Font("Helvetica-BoldOblique");
    public static final PDType1Font COURIER = new PDType1Font("Courier");
    public static final PDType1Font COURIER_BOLD = new PDType1Font("Courier-Bold");
    public static final PDType1Font COURIER_OBLIQUE = new PDType1Font("Courier-Oblique");
    public static final PDType1Font COURIER_BOLD_OBLIQUE = new PDType1Font("Courier-BoldOblique");
    public static final PDType1Font SYMBOL = new PDType1Font("Symbol");
    public static final PDType1Font ZAPF_DINGBATS = new PDType1Font("ZapfDingbats");

    private final FontMetrics afm; // for standard 14 fonts
    private final Type1Font type1font; // embedded font
    private final Type1Equivalent type1Equivalent; // embedded or system font for rendering
    private final boolean isEmbedded;

    /**
     * Creates a Type 1 standard 14 font for embedding.
     *
     * @param baseFont One of the standard 14 PostScript names
     */
    private PDType1Font(String baseFont)
    {
        dict.setItem(COSName.SUBTYPE, COSName.TYPE1);
        dict.setName(COSName.BASE_FONT, baseFont);
        encoding = new WinAnsiEncoding();
        dict.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);

        afm = getAFMFromBaseFont(baseFont);
        if (afm == null)
        {
            throw new IllegalArgumentException("No AFM for font " + baseFont);
        }

        // todo: could load the PFB font here if we wanted to support Standard 14 embedding
        type1font = null;
        type1Equivalent = ExternalFonts.getType1EquivalentFont(getBaseFont());
        isEmbedded = false;
    }

    /**
     * Creates a new Type 1 font for embedding.
     *
     * @param doc PDF document to write to
     * @param afmIn AFM file stream
     * @param pfbIn PFB file stream
     * @throws IOException
     */
    public PDType1Font(PDDocument doc, InputStream afmIn, InputStream pfbIn) throws IOException
    {
        PDType1FontEmbedder embedder = new PDType1FontEmbedder(doc, dict, afmIn, pfbIn);
        encoding = embedder.getFontEncoding();
        afm = null; // only used for standard 14 fonts, not AFM fonts as we already have the PFB
        type1font = embedder.getType1Font();
        type1Equivalent = embedder.getType1Font();
        isEmbedded = true;
    }

    /**
     * Creates a Type 1 font from a Font dictionary in a PDF.
     * 
     * @param fontDictionary font dictionary
     */
    public PDType1Font(COSDictionary fontDictionary) throws IOException
    {
        super(fontDictionary);
        PDFontDescriptor fd = getFontDescriptor();
        Type1Font t1 = null;
        if (fd != null && fd instanceof PDFontDescriptorDictionary) // <-- todo: must be true
        {
            // a Type1 font may contain a Type1C font
            PDStream fontFile3 = ((PDFontDescriptorDictionary) fd).getFontFile3();
            if (fontFile3 != null)
            {
                throw new IllegalArgumentException("Use PDType1CFont for FontFile3");
            }

            // or it may contain a PFB
            PDStream fontFile = ((PDFontDescriptorDictionary) fd).getFontFile();
            if (fontFile != null)
            {
                try
                {
                    COSStream stream = fontFile.getStream();
                    int length1 = stream.getInt(COSName.LENGTH1);
                    int length2 = stream.getInt(COSName.LENGTH2);

                    // the PFB embedded as two segments back-to-back
                    byte[] bytes = fontFile.getByteArray();
                    byte[] segment1 = Arrays.copyOfRange(bytes, 0, length1);
                    byte[] segment2 = Arrays.copyOfRange(bytes, length1, length1 + length2);

                    t1 =  Type1Font.createWithSegments(segment1, segment2);
                }
                catch (IOException e)
                {
                    LOG.error("Can't read the embedded Type1 font " + fd.getFontName(), e);
                }
            }
        }
        isEmbedded = t1 != null;

        // try to find a suitable .pfb font to substitute
        if (t1 == null)
        {
            t1 = ExternalFonts.getType1Font(getBaseFont());
        }

        type1font = t1;

        // find a type 1-equivalent font to use for rendering, could even be a .ttf
        if (type1font != null)
        {
            type1Equivalent = type1font;
        }
        else
        {
            Type1Equivalent t1Equiv = ExternalFonts.getType1EquivalentFont(getBaseFont());
            if (t1Equiv != null)
            {
                type1Equivalent = t1Equiv;
            }
            else
            {
                LOG.warn("Using fallback font for " + getBaseFont());
                type1Equivalent = ExternalFonts.getType1FallbackFont(getFontDescriptor());
            }
        }

        // todo: for standard 14 only. todo: move this to a subclass "PDStandardType1Font" ?
        afm = getAFMFromBaseFont(getBaseFont()); // may be null (it usually is)

        readEncoding();
    }

    // todo: move this to a subclass?
    private FontMetrics getAFMFromBaseFont(String baseFont)
    {
        if (baseFont != null)
        {
            if (baseFont.contains("+"))
            {
                baseFont = baseFont.substring(baseFont.indexOf('+') + 1);
            }
            return AFM_MAP.get(baseFont);
        }
        return null;
    }

    /**
     * Returns the PostScript name of the font.
     */
    public String getBaseFont()
    {
        return dict.getNameAsString(COSName.BASE_FONT);
    }

    @Override
    public PDFontDescriptor getFontDescriptor()
    {
        PDFontDescriptor fd = super.getFontDescriptor();
        if (fd == null)
        {
            if (afm != null)
            {
                // this is for embedding fonts into PDFs, rather than for reading, though it works.
                fd = new PDFontDescriptorAFM(afm);
                setFontDescriptor(fd);
            }
        }
        return fd;
    }

    @Override
    public float getHeight(int code) throws IOException
    {
        String name = codeToName(code);
        if (afm != null)
        {
            String afmName = getEncoding().getName(code);
            return afm.getCharacterHeight(afmName); // todo: isn't this the y-advance, not the height?
        }
        else
        {
            return (float)type1Equivalent.getPath(name).getBounds().getHeight();
        }
    }

    @Override
    public float getWidthFromFont(int code) throws IOException
    {
        String name = codeToName(code);
        if (afm != null)
        {
            String afmName = getEncoding().getName(code);
            return afm.getCharacterWidth(afmName);
        }
        else
        {
            return type1Equivalent.getWidth(name);
        }
    }

    @Override
    public boolean isEmbedded()
    {
        return isEmbedded;
    }

    @Override
    public float getAverageFontWidth()
    {
        if (afm != null)
        {
            return afm.getAverageCharacterWidth();
        }
        else
        {
            return super.getAverageFontWidth();
        }
    }

    @Override
    public int readCode(InputStream in) throws IOException
    {
        return in.read();
    }

    @Override
    protected Encoding readEncodingFromFont() throws IOException
    {
        if (afm != null)
        {
            // read from AFM
            return new Type1Encoding(afm);
        }
        else
        {
            // extract from Type1 font/substitute
            if (type1Equivalent.getEncoding() != null)
            {
                return Type1Encoding.fromFontBox(type1Equivalent.getEncoding());
            }
            else
            {
                // default (only happens with TTFs)
                return StandardEncoding.INSTANCE;
            }
        }
    }

    /**
     * Returns the embedded or substituted Type 1 font, or null if there is none.
     */
    public Type1Font getType1Font()
    {
        return type1font;
    }

    @Override
    public Type1Equivalent getType1Equivalent()
    {
        return type1Equivalent;
    }

    @Override
    public String getName()
    {
        return getBaseFont();
    }

    @Override
    public BoundingBox getBoundingBox() throws IOException
    {
        return type1Equivalent.getFontBBox();
    }

    @Override
    public String codeToName(int code) throws IOException
    {
        String name = getEncoding().getName(code);
        if (isEmbedded() || type1Equivalent.hasGlyph(name))
        {
            return name;
        }
        else
        {
            // try alternative name
            String altName = ALT_NAMES.get(name);
            if (altName != null && !name.equals(".notdef") && type1Equivalent.hasGlyph(altName))
            {
                return altName;
            }
            else
            {
                // try unicode name
                String unicodes = getGlyphList().toUnicode(name);
                if (unicodes != null)
                {
                    if (unicodes.length() == 1)
                    {
                        String uniName = String.format("uni%04X", unicodes.codePointAt(0));
                        if (type1Equivalent.hasGlyph(uniName))
                        {
                            return uniName;
                        }
                    }
                }
            }
        }
        return ".notdef";
    }

    @Override
    public GeneralPath getPath(String name) throws IOException
    {
        // Adobe's Standard 14 fonts have an empty .notdef glyph, but Microsoft's don't
        // so we need to fake this glyph otherwise we get unwanted rectangles, see PDFBOX-2372
        if (".notdef".equals(name) && isStandard14())
        {
            return new GeneralPath();
        }
        else
        {
            return type1Equivalent.getPath(name);
        }
    }
}
