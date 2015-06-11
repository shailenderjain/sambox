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
package org.apache.pdfbox.cos;

import static org.apache.pdfbox.input.source.SeekableSources.inMemorySeekableSourceFrom;
import static org.apache.pdfbox.input.source.SeekableSources.inputStreamFrom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.pdfbox.filter.DecodeResult;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.input.source.SeekableSource;
import org.apache.pdfbox.util.IOUtils;

/**
 * This class represents a stream object in a PDF document.
 *
 * @author Ben Litchfield
 */
public class COSStream extends COSDictionary implements Closeable
{
    private LazySeekableSourceViewHolder existing;
    private byte[] filtered;
    private byte[] unfiltered;
    private DecodeResult decodeResult;

    public COSStream()
    {
    }

    /**
     * @param dictionary The dictionary that is associated with this stream.
     */
    public COSStream(COSDictionary dictionary)
    {
        super(dictionary);
    }

    /**
     * Creates a stream with the given dictionary and where filtered data is a view of the given {@link SeekableSource}.
     * 
     * @param dictionary The dictionary that is associated with this stream.
     * @param seekableSource the source where filtered data is read from
     * @param startingPosition starting position of the stream data in the {@link SeekableSource}
     * @param length the length of the stream data
     */
    public COSStream(COSDictionary dictionary, SeekableSource seekableSource,
            long startingPosition, long length)
    {
        super(dictionary);
        this.existing = new LazySeekableSourceViewHolder(seekableSource, startingPosition, length);
    }

    /**
     * @return the (encoded) stream with all of the filters applied.
     * @throws IOException when encoding/decoding causes an exception
     */
    public InputStream getFilteredStream() throws IOException
    {
        if (existing != null)
        {
            return inputStreamFrom(existing.get());
        }
        if (getFilters() != null)
        {
            if (filtered == null)
            {
                doEncode();
            }
            return new MyByteArrayInputStream(filtered);
        }
        return new MyByteArrayInputStream(unfiltered);
    }

    /**
     * @return the (encoded) {@link SeekableSource} with all of the filters applied.
     * @throws IOException when encoding/decoding causes an exception
     */
    public SeekableSource getFilteredSource() throws IOException
    {
        if (existing != null)
        {
            return existing.get();
        }
        if (getFilters() != null)
        {
            if (filtered == null)
            {
                doEncode();
            }
            return inMemorySeekableSourceFrom(filtered);
        }
        return inMemorySeekableSourceFrom(unfiltered);
    }

    /**
     * @return the length of the encoded stream as long
     * @throws IOException
     */
    public long getFilteredLength() throws IOException
    {
        if (existing != null)
        {
            return existing.length;
        }
        if (getFilters() != null)
        {
            if (filtered == null)
            {
                doEncode();
            }
            return Optional.ofNullable(filtered).map(f -> f.length).orElse(0);
        }
        return Optional.ofNullable(unfiltered).map(f -> f.length).orElse(0);
    }

    /**
     * @return the (decoded) stream with all of the filters applied.
     * @throws IOException when encoding/decoding causes an exception
     */
    public InputStream getUnfilteredStream() throws IOException
    {
        if (getFilters() != null)
        {
            if (unfiltered == null)
            {
                doDecode();
            }
            return new MyByteArrayInputStream(unfiltered);
        }
        return getStreamToDecode();
    }

    /**
     * @return the (decoded) {@link SeekableSource} with all of the filters applied.
     * @throws IOException when encoding/decoding causes an exception
     */
    public SeekableSource getUnfilteredSource() throws IOException
    {
        if (getFilters() != null)
        {
            if (unfiltered == null)
            {
                doDecode();
            }
        }
        if (unfiltered != null)
        {
            return inMemorySeekableSourceFrom(unfiltered);
        }
        if (existing != null)
        {
            return existing.get();
        }
        return inMemorySeekableSourceFrom(filtered);
    }

    /**
     * @return the length of the decoded stream as long
     * @throws IOException
     */
    public long getUnfilteredLength() throws IOException
    {
        if (getFilters() != null)
        {
            if (unfiltered == null)
            {
                doDecode();
            }
        }
        if (unfiltered != null)
        {
            return Optional.ofNullable(unfiltered).map(f -> f.length).orElse(0);
        }
        if (existing != null)
        {
            return existing.length;
        }
        return Optional.ofNullable(filtered).map(f -> f.length).orElse(0);
    }

    /**
     * @return the repaired stream parameters dictionary
     * @throws IOException when encoding/decoding causes an exception
     */
    public DecodeResult getDecodeResult() throws IOException
    {
        if (unfiltered == null)
        {
            doDecode();
        }

        if (unfiltered == null || decodeResult == null)
        {
            StringBuilder filterInfo = new StringBuilder();
            COSBase filters = getFilters();
            if (filters != null)
            {
                filterInfo.append(" - filter: ");
                if (filters instanceof COSName)
                {
                    filterInfo.append(((COSName) filters).getName());
                }
                else if (filters instanceof COSArray)
                {
                    COSArray filterArray = (COSArray) filters;
                    for (int i = 0; i < filterArray.size(); i++)
                    {
                        if (filterArray.size() > 1)
                        {
                            filterInfo.append(", ");
                        }
                        filterInfo.append(((COSName) filterArray.get(i)).getName());
                    }
                }
            }
            String subtype = getNameAsString(COSName.SUBTYPE);
            throw new IOException(subtype + " stream was not read" + filterInfo);
        }
        return decodeResult;
    }

    @Override
    public void accept(COSVisitor visitor) throws IOException
    {
        visitor.visit(this);
    }

    /**
     * This will decode the physical byte stream applying all of the filters to the stream.
     *
     * @throws IOException If there is an error applying a filter to the stream.
     */
    private void doDecode() throws IOException
    {
        COSBase filters = getFilters();
        if (filters == null)
        {
            decodeResult = DecodeResult.DEFAULT;
        }
        else if (filters instanceof COSName)
        {
            unfiltered = decode((COSName) filters, 0, getStreamToDecode());
        }
        else if (filters instanceof COSArray)
        {
            unfiltered = decodeChain((COSArray) filters, getStreamToDecode());
        }
        else
        {
            throw new IOException("Unknown filter type:" + filters);
        }
    }

    private InputStream getStreamToDecode() throws IOException
    {
        if (existing != null)
        {
            return inputStreamFrom(existing.get());
        }
        return new MyByteArrayInputStream(filtered);
    }

    private byte[] decodeChain(COSArray filters, InputStream startingFrom) throws IOException
    {
        if (filters.size() > 0)
        {
            byte[] tmpResult = new byte[0];
            InputStream input = startingFrom;
            for (int i = 0; i < filters.size(); i++)
            {
                COSName filterName = (COSName) filters.getObject(i);
                tmpResult = decode(filterName, i, input);
                input = new MyByteArrayInputStream(tmpResult);
            }
            return tmpResult;
        }
        decodeResult = DecodeResult.DEFAULT;
        return null;
    }

    private byte[] decode(COSName filterName, int filterIndex, InputStream toDecode)
            throws IOException
    {
        if (toDecode.available() > 0)
        {
            Filter filter = FilterFactory.INSTANCE.getFilter(filterName);
            try (MyByteArrayOutputStream out = new MyByteArrayOutputStream())
            {
                decodeResult = filter.decode(toDecode, out, this, filterIndex);
                return out.toByteArray();
            }
            finally
            {
                IOUtils.close(toDecode);
            }
        }
        return new byte[0];

    }

    /**
     * This will encode the logical byte stream applying all of the filters to the stream.
     *
     * @throws IOException If there is an error applying a filter to the stream.
     */
    private void doEncode() throws IOException
    {

        COSBase filters = getFilters();
        if (filters instanceof COSName)
        {
            filtered = encode((COSName) filters, 0, new MyByteArrayInputStream(unfiltered));
        }
        else if (filters instanceof COSArray)
        {
            filtered = encodeChain((COSArray) filters, new MyByteArrayInputStream(unfiltered));
        }
    }

    private byte[] encode(COSName filterName, int filterIndex, InputStream toEncode)
            throws IOException
    {
        Filter filter = FilterFactory.INSTANCE.getFilter(filterName);
        try (MyByteArrayOutputStream encoded = new MyByteArrayOutputStream())
        {
            filter.encode(toEncode, encoded, this, filterIndex);
            return encoded.toByteArray();
        }
    }

    private byte[] encodeChain(COSArray filters, InputStream startingFrom) throws IOException
    {
        if (filters.size() > 0)
        {
            byte[] tmpResult = new byte[0];
            InputStream input = startingFrom;
            for (int i = filters.size() - 1; i >= 0; i--)
            {
                COSName filterName = (COSName) filters.getObject(i);
                tmpResult = encode(filterName, i, input);
                input = new MyByteArrayInputStream(tmpResult);
            }
            return tmpResult;
        }
        return null;
    }

    /**
     * This will return the filters to apply to the byte stream. The method will return - null if no filters are to be
     * applied - a COSName if one filter is to be applied - a COSArray containing COSNames if multiple filters are to be
     * applied
     *
     * @return the COSBase object representing the filters
     */
    public COSBase getFilters()
    {
        return getDictionaryObject(COSName.FILTER);
    }

    /**
     * Creates a new stream for which filtered byte should be written to. You probably don't want this but want to use
     * the createUnfilteredStream, which is used to write raw bytes to.
     *
     * @return A stream that can be written to.
     */
    public OutputStream createFilteredStream()
    {
        IOUtils.closeQuietly(existing);
        unfiltered = null;
        existing = null;
        filtered = null;
        return new MyByteArrayOutputStream(bytes -> {
            this.filtered = bytes;
        });
    }

    /**
     * set the filters to be applied to the stream.
     *
     * @param filters The filters to set on this stream.
     * @throws IOException If there is an error clearing the old filters.
     */
    public void setFilters(COSBase filters) throws IOException
    {
        if (unfiltered == null)
        {
            try (InputStream in = getUnfilteredStream())
            {
                try (MyByteArrayOutputStream out = new MyByteArrayOutputStream(bytes -> {
                    this.unfiltered = bytes;
                }))
                {
                    IOUtils.copy(in, out);
                }

            }

        }
        setItem(COSName.FILTER, filters);
        IOUtils.closeQuietly(existing);
        existing = null;
        filtered = null;
    }

    /**
     * This will create an output stream that can be written to.
     *
     * @return An output stream which raw data bytes should be written to.
     *
     * @throws IOException If there is an error creating the stream.
     */
    public OutputStream createUnfilteredStream()
    {
        filtered = null;
        IOUtils.closeQuietly(existing);
        existing = null;
        unfiltered = null;
        return new MyByteArrayOutputStream(bytes -> {
            this.unfiltered = bytes;
        });
    }

    @Override
    public void close()
    {
        IOUtils.closeQuietly(existing);
        existing = null;
        unfiltered = null;
        filtered = null;
    }

    static class MyByteArrayOutputStream extends ByteArrayOutputStream
    {
        private Optional<Consumer<byte[]>> onClose;

        MyByteArrayOutputStream()
        {
            this(null);
        }

        MyByteArrayOutputStream(Consumer<byte[]> onClose)
        {
            super(0);
            this.onClose = Optional.ofNullable(onClose);
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            // TODO find a way to avoid copying the array
            onClose.ifPresent(c -> c.accept(toByteArray()));
        }
    }

    static class MyByteArrayInputStream extends ByteArrayInputStream
    {
        MyByteArrayInputStream(byte[] bytes)
        {
            super(Optional.ofNullable(bytes).orElse(new byte[0]));
        }
    }

    /**
     * Holder for a view of a portion of the given {@link SeekableSource}
     * 
     * @author Andrea Vacondio
     */
    private static class LazySeekableSourceViewHolder implements Closeable
    {
        private WeakReference<SeekableSource> sourceRef;
        private long startingPosition;
        private long length;

        private SeekableSource view;

        public LazySeekableSourceViewHolder(SeekableSource source, long startingPosition,
                long length)
        {
            this.sourceRef = new WeakReference<>(source);
            this.startingPosition = startingPosition;
            this.length = length;
        }

        SeekableSource get() throws IOException
        {
            SeekableSource source = Optional
                    .ofNullable(this.sourceRef.get())
                    .filter(SeekableSource::isOpen)
                    .orElseThrow(
                            () -> new IllegalStateException(
                                    "The original SeekableSource has been closed."));
            if (view == null)
            {
                view = source.view(startingPosition, length);
            }
            return view;
        }

        @Override
        public void close() throws IOException
        {
            IOUtils.close(view);
        }
    }

}
