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
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.awt.Point;

class ImageFactory
{
    private ImageFactory()
    {
    }

    public static BufferedImage create(PngConfig config, Map props)
    throws IOException
    {
        if (config.getMetadataOnly())
            return null;

        int width     = PngImage.getInt(props, PngImage.WIDTH);
        int height    = PngImage.getInt(props, PngImage.HEIGHT);
        int colorType = PngImage.getInt(props, PngImage.COLOR_TYPE);
        int bitDepth  = PngImage.getInt(props, PngImage.BIT_DEPTH);
        int interlace = PngImage.getInt(props, PngImage.INTERLACE);

        long gamma = 45455L;
        if (props.containsKey(PngImage.GAMMA))
            gamma = ((Number)props.get(PngImage.GAMMA)).longValue();

        int[] gammaTable = calcGammaTable(config, bitDepth, gamma);
        boolean interlaced = interlace == PngImage.INTERLACE_ADAM7;
        int samples = getSamples(colorType);
        ColorModel colorModel = createColorModel(props, gammaTable);
        WritableRaster raster = colorModel.createCompatibleWritableRaster(width, height);
        // WritableRaster raster = createRaster(bitDepth, samples, width, height);

        InputStream in;
        in = new MultiByteArrayInputStream((List)props.get(PngImage.DATA));
        in = new InflaterInputStream(in, new Inflater(), 0x2000);
        // System.err.println("props=" + props);
        BufferedImage image = new BufferedImage(colorModel, raster, false, null);
        // TODO: if not progressive, initialize to fully transparent

        PixelProcessor pp = BasicPixelProcessor.getInstance();
        if (colorModel instanceof ComponentColorModel) {
            if (props.containsKey(PngImage.TRANSPARENCY_GRAY)) {
                pp = new TransGammaPixelProcessor(gammaTable, new int[]{
                    PngImage.getInt(props, PngImage.TRANSPARENCY_GRAY),
                });
            } else if (props.containsKey(PngImage.TRANSPARENCY_RED)) {
                pp = new TransGammaPixelProcessor(gammaTable, new int[]{
                    PngImage.getInt(props, PngImage.TRANSPARENCY_RED),
                    PngImage.getInt(props, PngImage.TRANSPARENCY_GREEN),
                    PngImage.getInt(props, PngImage.TRANSPARENCY_BLUE),
                });
            } else {
                pp = new GammaPixelProcessor(gammaTable);
            }
        }
        if (config.isProgressive() && interlaced)
            pp = new ProgressivePixelProcessor(pp);

        Defilterer d = new Defilterer(in, raster, bitDepth, samples, pp);
        if (interlaced) {
            d.defilter(0, 0, 8, 8, (width + 7) / 8, (height + 7) / 8);
            config.handleFrame(image, 6);
            d.defilter(4, 0, 8, 8, (width + 3) / 8, (height + 7) / 8);
            config.handleFrame(image, 5);
            d.defilter(0, 4, 4, 8, (width + 3) / 4, (height + 3) / 8);
            config.handleFrame(image, 4);
            d.defilter(2, 0, 4, 4, (width + 1) / 4, (height + 3) / 4);
            config.handleFrame(image, 3);
            d.defilter(0, 2, 2, 4, (width + 1) / 2, (height + 1) / 4);
            config.handleFrame(image, 2);
            d.defilter(1, 0, 2, 2, width / 2, (height + 1) / 2);
            config.handleFrame(image, 1);
            d.defilter(0, 1, 1, 2, width, height / 2);
            config.handleFrame(image, 0);
        } else {
            d.defilter(0, 0, 1, 1, width, height);
            config.handleFrame(image, 0);
        }
        return image;
    }

    private static int[] calcGammaTable(PngConfig config, int bitDepth, long fileGamma)
    {
        int size = 1 << ((bitDepth == 16) ? 16 : 8);
        int[] gammaTable = new int[size];
        double decodingExponent =
            (config.getUserExponent() * 100000d / (fileGamma * config.getDisplayExponent()));
        for (int i = 0; i < size; i++)
            gammaTable[i] = (int)(Math.pow((double)i / (size - 1), decodingExponent) * (size - 1));
        return gammaTable;
    }

    private static ColorModel createColorModel(Map props, int[] gammaTable)
    {
        int colorType = PngImage.getInt(props, PngImage.COLOR_TYPE);
        int bitDepth  = PngImage.getInt(props, PngImage.BIT_DEPTH);

        if (colorType == PngImage.COLOR_TYPE_PALETTE ||
            (colorType == PngImage.COLOR_TYPE_GRAY && bitDepth < 16)) {
            byte[] r = applyGamma((byte[])props.get(PngImage.PALETTE_RED), gammaTable);
            byte[] g = applyGamma((byte[])props.get(PngImage.PALETTE_GREEN), gammaTable);
            byte[] b = applyGamma((byte[])props.get(PngImage.PALETTE_BLUE), gammaTable);
            byte[] a = (byte[])props.get(PngImage.PALETTE_ALPHA);
            if (a != null) {
                return new IndexColorModel(bitDepth, r.length, r, g, b, a);
            } else {
                int trans = -1;
                if (props.containsKey(PngImage.TRANSPARENCY_GRAY)) {
                    trans = PngImage.getInt(props, PngImage.TRANSPARENCY_GRAY);
                    trans = trans * 255 / ((1 << bitDepth) - 1);
                }
                return new IndexColorModel(bitDepth, r.length, r, g, b, trans);
            }
        } else {
            int dataType = (bitDepth == 16) ?
                DataBuffer.TYPE_USHORT : DataBuffer.TYPE_BYTE;
            boolean hasAlpha =
                colorType == PngImage.COLOR_TYPE_RGB_ALPHA ||
                colorType == PngImage.COLOR_TYPE_GRAY_ALPHA ||
                props.containsKey(PngImage.TRANSPARENCY_GRAY) ||
                props.containsKey(PngImage.TRANSPARENCY_RED);
            return new ComponentColorModel(getColorSpace(colorType),
                                           hasAlpha,
                                           false,
                                           hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE,
                                           dataType);
        }
    }
    
    private static int getSamples(int colorType)
    {
        switch (colorType) {
        case PngImage.COLOR_TYPE_GRAY_ALPHA: return 2;
        case PngImage.COLOR_TYPE_RGB:        return 3;
        case PngImage.COLOR_TYPE_RGB_ALPHA:  return 4;
        }
        return 1;
    }

    private static ColorSpace getColorSpace(int colorType)
    {
        switch (colorType) {
        case PngImage.COLOR_TYPE_GRAY:
        case PngImage.COLOR_TYPE_GRAY_ALPHA:
            return ColorSpace.getInstance(ColorSpace.CS_GRAY);
        default:
            return ColorSpace.getInstance(ColorSpace.CS_sRGB);
        }
    }

    private static byte[] applyGamma(byte[] palette, int[] gammaTable)
    {
        if (palette == null)
            return null;
        if (gammaTable == null)
            return palette;

        int size = palette.length;
        byte[] copy = new byte[size];
        for (int i = 0; i < size; i++)
            copy[i] = (byte)gammaTable[0xFF & palette[i]];
        return copy;
    }
}
