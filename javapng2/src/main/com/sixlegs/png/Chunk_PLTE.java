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

import java.io.*;
import java.util.Map;

class Chunk_PLTE
extends PngChunk
{
    public void read(int type, PngInputStream in, PngImage png)
    throws IOException
    {
        int length = in.getRemaining();
        if (length % 3 != 0)
            throw new PngError("PLTE chunk length indivisible by 3");
        switch (png.getColorType()) {
        case PngConstants.COLOR_TYPE_PALETTE:
            int size = length / 3;
            if (size > (2 << png.getBitDepth()) || size > 256)
                throw new PngError("Too many palette entries");
            break;
        case PngConstants.COLOR_TYPE_GRAY:
        case PngConstants.COLOR_TYPE_GRAY_ALPHA:
            throw new PngWarning("PLTE chunk found in grayscale image");
        }
        byte[] palette = new byte[length];
        in.readFully(palette);
        png.getProperties().put(PngConstants.PALETTE, palette);
    }
}