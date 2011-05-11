package org.dynmap.kzedmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.dynmap.Client;
import org.dynmap.ColorScheme;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.debug.Debug;

public class DefaultTileRenderer implements MapTileRenderer {
    protected static final Color translucent = new Color(0, 0, 0, 0);
    protected String name;
    protected int maximumHeight = 127;
    protected ColorScheme colorScheme;
    
    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();
    protected Color highlightColor = new Color(255, 0, 0);

    @Override
    public String getName() {
        return name;
    }

    public DefaultTileRenderer(Map<String, Object> configuration) {
        name = (String) configuration.get("prefix");
        Object o = configuration.get("maximumheight");
        if (o != null) {
            maximumHeight = Integer.parseInt(String.valueOf(o));
            if (maximumHeight > 127)
                maximumHeight = 127;
        }
        colorScheme = ColorScheme.getScheme((String)configuration.get("colorscheme"));
    }

    public boolean render(KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        BufferedImage im = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);

        WritableRaster r = im.getRaster();
        boolean isempty = true;

        int ix = KzedMap.anchorx + tile.px / 2 + tile.py / 2 - ((127-maximumHeight)/2);
        int iy = maximumHeight;
        int iz = KzedMap.anchorz + tile.px / 2 - tile.py / 2 + ((127-maximumHeight)/2);

        int jx, jz;

        int x, y;

        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                Color c1 = scan(world, jx, iy, jz, 0);
                Color c2 = scan(world, jx, iy, jz, 2);
                isempty = isempty && c1 == translucent && c2 == translucent;
                r.setPixel(x, y, new int[] {
                    c1.getRed(),
                    c1.getGreen(),
                    c1.getBlue() });
                r.setPixel(x - 1, y, new int[] {
                    c2.getRed(),
                    c2.getGreen(),
                    c2.getBlue() });

                jx++;
                jz++;

            }

            y++;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                Color c1 = scan(world, jx, iy, jz, 2);
                jx++;
                jz++;
                Color c2 = scan(world, jx, iy, jz, 0);
                isempty = isempty && c1 == translucent && c2 == translucent;
                r.setPixel(x, y, new int[] {
                    c1.getRed(),
                    c1.getGreen(),
                    c1.getBlue() });
                r.setPixel(x - 1, y, new int[] {
                    c2.getRed(),
                    c2.getGreen(),
                    c2.getBlue() });
            }

            y++;

            ix++;
            iz--;
        }

        /* Hand encoding and writing file off to MapManager */
        final File fname = outputFile;
        final KzedMapTile mtile = tile;
        final BufferedImage img = im;
        MapManager.mapman.enqueueImageWrite(new Runnable() {
        	public void run() {
        		Debug.debug("saving image " + fname.getPath());        
        		try {
        			ImageIO.write(img, "png", fname);
        		} catch (IOException e) {
        			Debug.error("Failed to save image: " + fname.getPath(), e);
        		} catch (java.lang.NullPointerException e) {
        			Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
        		}
        		img.flush();
        		mtile.file = fname;
        		((KzedMap)mtile.getMap()).invalidateTile(
        			new KzedZoomedMapTile(mtile.getWorld(), (KzedMap) mtile.getMap(), mtile));
        		MapManager.mapman.pushUpdate(mtile.getWorld(), 
    				new Client.Tile(mtile.getFilename()));        		
        	}
        });        

        return !isempty;
    }

    protected Color scan(World world, int x, int y, int z, int seq) {
        for (;;) {
            if (y < 0)
                return translucent;

            int id = world.getBlockTypeIdAt(x, y, z);

            switch (seq) {
            case 0:
                x--;
                break;
            case 1:
                y--;
                break;
            case 2:
                z++;
                break;
            case 3:
                y--;
                break;
            }

            seq = (seq + 1) & 3;

            if (id != 0) {
                if (highlightBlocks.contains(id)) {
                    return highlightColor;
                }
                Color[] colors = colorScheme.colors.get(id);
                if (colors != null) {
                    Color c = colors[seq];
                    if (c.getAlpha() > 0) {
                        /* we found something that isn't transparent! */
                        if (c.getAlpha() == 255) {
                            /* it's opaque - the ray ends here */
                            return c;
                        }

                        /* this block is transparent, so recurse */
                        Color bg = scan(world, x, y, z, seq);

                        int cr = c.getRed();
                        int cg = c.getGreen();
                        int cb = c.getBlue();
                        int ca = c.getAlpha();
                        cr *= ca;
                        cg *= ca;
                        cb *= ca;
                        int na = 255 - ca;

                        return new Color((bg.getRed() * na + cr) >> 8, (bg.getGreen() * na + cg) >> 8, (bg.getBlue() * na + cb) >> 8);
                    }
                }
            }
        }
    }
}
