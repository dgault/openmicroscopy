/*
 * org.openmicroscopy.shoola.env.data.model.PixelsDescription
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

package org.openmicroscopy.shoola.env.data.model;

//Java imports

//Third-party libraries

//Application-internal dependencies

/** 
 * 
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
public class PixelsDescription
	implements DataObject
{
	/** Attribute_id in DB. */
	private int		id;
	private int		imageServerID;
	private int 	sizeX;
	private int 	sizeY;
	private int 	sizeZ;
	private int 	sizeC;
	private int 	sizeT;
	private int 	bitsPerPixel;
	private String	imageServerURL;
	
	public PixelsDescription() {}
	
	public PixelsDescription(int id, int imageServerID, int sizeX, int sizeY, 
							int sizeZ, int sizeC, int sizeT, int bitsPerPixel,
							String imageServerURL)
	{
		this.id = id;
		this.imageServerID = imageServerID;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.sizeC = sizeC;
		this.sizeT = sizeT;
		this.bitsPerPixel = bitsPerPixel;
		this.imageServerURL = imageServerURL;	
	}
	
	/** Required by the DataObject interface. */
	public DataObject makeNew()
	{
		return new PixelsDescription();
	}
	
	public int getBitsPerPixel()
	{
		return bitsPerPixel;
	}
	
	public int getSizeC()
	{
		return sizeC;
	}

	public int getSizeT()
	{
		return sizeT;
	}

	public int getSizeX()
	{
		return sizeX;
	}

	public int getSizeY()
	{
		return sizeY;
	}

	public int getSizeZ()
	{
		return sizeZ;
	}

	public void setBitsPerPixel(int bitsPerPixel)
	{
		this.bitsPerPixel = bitsPerPixel;
	}

	public void setSizeC(int sizeC)
	{
		this.sizeC = sizeC;
	}

	public void setSizeT(int sizeT)
	{
		this.sizeT = sizeT;
	}

	public void setSizeX(int sizeX)
	{
		this.sizeX = sizeX;
	}

	public void setSizeY(int sizeY)
	{
		this.sizeY = sizeY;
	}

	public void setSizeZ(int sizeZ)
	{
		this.sizeZ = sizeZ;
	}

	public String getImageServerUrl()
	{
		return imageServerURL;
	}

	public void setImageServerUrl(String imageServerURL)
	{
		this.imageServerURL = imageServerURL;
	}

	public int getID()
	{
		return id;
	}

	public void setID(int id)
	{
		this.id = id;
	}

	public int getImageServerID()
	{
		return imageServerID;
	}

	public void setImageServerID(int imageServerID)
	{
		this.imageServerID = imageServerID;
	}

}
