/*
com.sixlegs.image.png - Java package to read and display PNG images
Copyright (C) 1998-2005 Chris Nokleberg

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.

You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*/

package com.sixlegs.png;

import java.awt.Color;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

class Chunk_tRNS
extends PngChunk
{
    public Chunk_tRNS()
    {
        super(tRNS);
    }

    public void read(PngInputStream in, int length, PngImage png)
    throws IOException
    {
        int colorType = png.getColorType();
        Map props = png.getProperties();

        switch (colorType) {
        case PngImage.COLOR_TYPE_GRAY:
            checkLength(length, 2);
            props.put(PngImage.TRANSPARENCY_GRAY, Integers.valueOf(in.readUnsignedShort()));
            break;

        case PngImage.COLOR_TYPE_RGB:
            checkLength(length, 6);
            props.put(PngImage.TRANSPARENCY_RED,   Integers.valueOf(in.readUnsignedShort()));
            props.put(PngImage.TRANSPARENCY_GREEN, Integers.valueOf(in.readUnsignedShort()));
            props.put(PngImage.TRANSPARENCY_BLUE,  Integers.valueOf(in.readUnsignedShort()));
            break;

        case PngImage.COLOR_TYPE_PALETTE:
            byte[] r = (byte[])png.getProperty(PngImage.PALETTE_RED);
            if (length > r.length)
                throw new PngError("Too many transparency palette entries (" + length + " > " + r.length + ")");

            byte[] alpha = new byte[r.length];
            Arrays.fill(alpha, (byte)0xFF);
            for (int i = 0; i < length; i++)
                alpha[i] = in.readByte();

            props.put(PngImage.PALETTE_ALPHA, alpha);
            break;

        default:
            throw new PngError("tRNS prohibited for color type " + colorType);
        }
    }
}