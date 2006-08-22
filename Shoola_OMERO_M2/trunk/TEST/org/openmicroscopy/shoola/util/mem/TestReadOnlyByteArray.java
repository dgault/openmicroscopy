/*
 * org.openmicroscopy.shoola.util.mem.TestReadOnlyByteArray
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2004 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package org.openmicroscopy.shoola.util.mem;


//Java imports

//Third-party libraries
import junit.framework.TestCase;

//Application-internal dependencies

/** 
 * Tests the normal operation of <code>ReadOnlyByteArray</code> and possible
 * exceptions.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author  <br>Andrea Falconi &nbsp;&nbsp;&nbsp;&nbsp;
 * 				<a href="mailto:a.falconi@dundee.ac.uk">
 * 					a.falconi@dundee.ac.uk</a>
 * @version 2.2
 * <small>
 * (<b>Internal version:</b> $Revision$ $Date$)
 * </small>
 * @since OME2.2
 */
public class TestReadOnlyByteArray
    extends TestCase
{

    public void testReadOnlyByteArray()
    {
        try {
            new ReadOnlyByteArray(null, 0, 0);
            fail("Shouldn't accept null base.");
        } catch (NullPointerException npe) {}
        
        byte[] base = new byte[0];
        try {
            new ReadOnlyByteArray(base, -1, 0);
            fail("Shouldn't accept negative offset.");
        } catch (IllegalArgumentException iae) {}
        try {
            new ReadOnlyByteArray(base, 0, -1);
            fail("Shouldn't accept negative length.");
        } catch (IllegalArgumentException iae) {}
        try {
            new ReadOnlyByteArray(base, 1, 0);
            fail("Shouldn't accept inconsistent [offset, offset+length]."); 
        } catch (IllegalArgumentException iae) {}
    }

    public void testGetEmptyArray()
    {
        byte[] base = new byte[] {0, 1, 2};
        ReadOnlyByteArray roba = new ReadOnlyByteArray(base, 1, 0);
        try {
            roba.get(0);
            fail("Shouldn't accept index 0 if length is 0.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            roba.get(-1);
            fail("Shouldn't accept negative index.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            roba.get(1);
            fail("Shouldn't accept index greater than length-1.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
    }
    
    public void testGet1LengthArray()
    {
        byte[] base = new byte[] {0, 1};
        ReadOnlyByteArray roba = new ReadOnlyByteArray(base, 1, 1);
        assertEquals("Calculated wrong base offset.", 1, roba.get(0));
        try {
            roba.get(-1);
            fail("Shouldn't accept negative index.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            roba.get(1);
            fail("Shouldn't accept index greater than length-1.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
    }
    
    public void testGet2LengthArray()
    {
        byte[] base = new byte[] {0, 1, 2, 3, 4};
        ReadOnlyByteArray roba = new ReadOnlyByteArray(base, 2, 2);
        assertEquals("Calculated wrong base offset.", 2, roba.get(0));
        assertEquals("Calculated wrong base offset.", 3, roba.get(1));
        try {
            roba.get(-1);
            fail("Shouldn't accept negative index.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            roba.get(2);
            fail("Shouldn't accept index greater than length-1.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
    }
    
    public void testGet3LengthArray()
    {
        byte[] base = new byte[] {0, 1, 2};
        ReadOnlyByteArray roba = new ReadOnlyByteArray(base, 0, 3);
        assertEquals("Calculated wrong base offset.", 0, roba.get(0));
        assertEquals("Calculated wrong base offset.", 1, roba.get(1));
        assertEquals("Calculated wrong base offset.", 2, roba.get(2));
        try {
            roba.get(-1);
            fail("Shouldn't accept negative index.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
        try {
            roba.get(3);
            fail("Shouldn't accept index greater than length-1.");
        } catch (ArrayIndexOutOfBoundsException aiobe) {}
    }

}
