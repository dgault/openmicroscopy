/*
 * Copyright (C) 2013 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openmicroscopy.shoola.keywords;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.swing.JPanel;

import org.robotframework.abbot.finder.BasicFinder;
import org.robotframework.abbot.finder.ComponentNotFoundException;
import org.robotframework.abbot.finder.Matcher;
import org.robotframework.abbot.finder.MultipleComponentsFoundException;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 * Robot Framework SwingLibrary keyword library offering methods for checking thumbnails.
 * @author m.t.b.carroll@dundee.ac.uk
 * @since 4.4.9
 */
public class ThumbnailCheckLibrary
{
    /** Allow Robot Framework to instantiate this library only once. */
    public static final String ROBOT_LIBRARY_SCOPE = "GLOBAL";

    /**
     * An iterator over the integer pixel values of a rendered image,
     * first increasing <em>x</em>, then <em>y</em> when <em>x</em> wraps back to 0.
     * This is written so as to be scalable over arbitrary image sizes
     * and to not cause heap allocations during the iteration.
     * @author m.t.b.carroll@dundee.ac.uk
     */
    private static class IteratorIntPixel {
        final Raster raster;
        final int width;
        final int height;
        final int[] pixel = new int[1];
        int x = 0;
        int y = 0;

        /**
         * Create a new pixel iterator for the given image.
         * The image is assumed to be of a type that packs data for each pixel into an <code>int</code>.
         * @param image the image over whose pixels to iterate
         */
        IteratorIntPixel(RenderedImage image) {
            this.raster = image.getData();
            this.width = image.getWidth();
            this.height = image.getHeight();
        }

        /**
         * @return if any pixels remain to be read with {@link #next()}
         */
        boolean hasNext() {
            return y < height;
        }

        /**
         * @return the next pixel
         * @throws NoSuchElementException if no more pixels remain
         */
        int next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            raster.getDataElements(x, y, pixel);
            if (++x == width) {
                x = 0;
                ++y;
            }
            return pixel[0];
        }
    }

    /**
     * Convert the thumbnail canvas for the image of the given filename into rasterized pixel data.
     * Each pixel is represented by an <code>int</code>.
     * @param imageFilename the name of the image whose thumbnail canvas is to be rasterized
     * @return the image on the thumbnail
     * @throws MultipleComponentsFoundException if multiple thumbnails are for the given image name
     * @throws ComponentNotFoundException if no thumbnails are for the given image name
     */
    private RenderedImage captureThumbnailImage(final String imageFilename)
        throws ComponentNotFoundException, MultipleComponentsFoundException {
        final JPanel thumbnailCanvas = (JPanel) new BasicFinder().find(new Matcher() {
            public boolean matches(Component component) {
                if (component instanceof JPanel) {
                    final String name = component.getName();
                    return name != null && name.startsWith("thumbnail for ") && name.endsWith("/" + imageFilename);
                }
                return false;
            }});
        final int width = thumbnailCanvas.getWidth();
        final int height = thumbnailCanvas.getHeight();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        if (graphics == null) {
            throw new RuntimeException("thumbnail is not displayable");
        }
        thumbnailCanvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    /**
     * <table>
     *   <td>Is Thumbnail Monochromatic</td>
     *   <td>name of image whose thumbnail is queried</td>
     * </table>
     * @param imageFilename the name of the image
     * @return if the image's thumbnail canvas is solidly one color
     * @throws MultipleComponentsFoundException if multiple thumbnails exist for the given name
     * @throws ComponentNotFoundException if no thumbnails exist for the given name
     */
    public boolean isThumbnailMonochromatic(String imageFilename)
    throws ComponentNotFoundException, MultipleComponentsFoundException {
        final RenderedImage image = captureThumbnailImage(imageFilename);
        final IteratorIntPixel pixels = new IteratorIntPixel(image);
        if (!pixels.hasNext()) {
            throw new RuntimeException("thumbnail image has no pixels");
        }
        final int oneColor = pixels.next();
        while (pixels.hasNext()) {
            if (pixels.next() != oneColor) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generate a reasonable hash of the thumbnail canvas for an image.
     * Intended for detecting if images are the same.
     * @param imageFilename the name of the image
     * @return the hash of the thumbnail canvas image
     * @throws MultipleComponentsFoundException if multiple thumbnails exist for the given name
     * @throws ComponentNotFoundException if no thumbnails exist for the given name
     */
    private String getThumbnailHash(String imageFilename)
    throws ComponentNotFoundException, MultipleComponentsFoundException {
        final RenderedImage image = captureThumbnailImage(imageFilename);
        final IteratorIntPixel pixels = new IteratorIntPixel(image);
        final Hasher hasher = Hashing.goodFastHash(128).newHasher();
        while (pixels.hasNext()) {
            hasher.putInt(pixels.next());
        }
        return hasher.hash().toString();
    }

    /**
     * <table>
     *   <td>Are Thumbnails Identical</td>
     *   <td>names of images whose thumbnails are queried</td>
     * </table>
     * @param imageFilenames the names of the images
     * @return if the images' thumbnail canvases show identical images
     * @throws MultipleComponentsFoundException if multiple thumbnails exist for a given name
     * @throws ComponentNotFoundException if no thumbnails exist for a given name
     */
    public boolean areThumbnailsIdentical(String... imageFilenames)
            throws ComponentNotFoundException, MultipleComponentsFoundException {
        final Set<String> hashes = new HashSet<String>();
        for (final String imageFilename : imageFilenames) {
            hashes.add(getThumbnailHash(imageFilename));
            if (hashes.size() > 1) {
                return false;
            }
        }
        return true;
    }
}
