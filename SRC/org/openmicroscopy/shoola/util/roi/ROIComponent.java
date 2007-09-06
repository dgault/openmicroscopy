/*
 * org.openmicroscopy.shoola.util.roi.ROIComponent 
 *
  *------------------------------------------------------------------------------
 *  Copyright (C) 2006-2007 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package org.openmicroscopy.shoola.util.roi;


//Java imports
import java.awt.Color;
import java.awt.Component;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.TreeMap;

//Third-party libraries

//Application-internal dependencies
import org.jhotdraw.draw.AttributeKeys;
import org.openmicroscopy.shoola.util.roi.exception.NoSuchROIException;
import org.openmicroscopy.shoola.util.roi.exception.ParsingException;
import org.openmicroscopy.shoola.util.roi.exception.ROICreationException;
import org.openmicroscopy.shoola.util.roi.figures.MeasurePointFigure;
import org.openmicroscopy.shoola.util.roi.figures.ROIFigure;
import org.openmicroscopy.shoola.util.roi.io.IOConstants;
import org.openmicroscopy.shoola.util.roi.io.XMLFileIOStrategy;
import org.openmicroscopy.shoola.util.roi.model.ROI;
import org.openmicroscopy.shoola.util.roi.model.ROICollection;
import org.openmicroscopy.shoola.util.roi.model.ROIRelationship;
import org.openmicroscopy.shoola.util.roi.model.ROIRelationshipList;
import org.openmicroscopy.shoola.util.roi.model.ROIShape;
import org.openmicroscopy.shoola.util.roi.model.ROIShapeRelationship;
import org.openmicroscopy.shoola.util.roi.model.ROIShapeRelationshipList;
import org.openmicroscopy.shoola.util.roi.model.ShapeList;
import org.openmicroscopy.shoola.util.roi.model.annotation.MeasurementAttributes;
import org.openmicroscopy.shoola.util.roi.model.util.Coord3D;
import org.openmicroscopy.shoola.util.roi.model.util.MeasurementUnits;
import org.openmicroscopy.shoola.util.ui.drawingtools.attributes.DrawingAttributes;

/** 
 * The ROI Component is the main interface to the object which control the 
 * creations, storage, deletion and manipulation of ROIs. 
 * 
 * The ROIComponent also accesses(currently) the IOStrategy for loading and 
 * saving ROIs.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author	Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * 	<a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
public class ROIComponent 
	extends Component 
{
		
	/** The default color of the text. */
	private static final Color			TEXT_COLOR = 
										IOConstants.DEFAULT_TEXT_COLOUR;

	
	/** The default color of the measurement text. */
	private static final Color			MEASUREMENT_COLOR = 
										IOConstants.DEFAULT_MEASUREMENT_TEXT_COLOUR;
	
	/** The default color used to fill area. */
	private static final Color			FILL_COLOR = 
										IOConstants.DEFAULT_FILL_COLOUR;

	/** The default color used to fill area alpha'ed <sp>. */
	private static final Color			FILL_COLOR_ALPHA = 
										IOConstants.DEFAULT_FILL_COLOUR_ALPHA;
	
	/** The default color of the text. */
	private static final double			FONT_SIZE = 
										IOConstants.DEFAULT_FONT_SIZE;

	
	/** The default width of the stroke. */
	private static final double			STROKE_WIDTH = 
										IOConstants.DEFAULT_STROKE_WIDTH;

	
	/** The default color of the stroke. */
	private static final Color			STROKE_COLOR = 
										IOConstants.DEFAULT_STROKE_COLOUR;

	
	/** The default color of the stroke alpha'ed <sp> to transparent. */
	private static final Color			STROKE_COLOR_ALPHA = 
										IOConstants.DEFAULT_STROKE_COLOUR_ALPHA;

	/** The main object for storing and manipulating ROIs. */
	private ROICollection				roiCollection;

	/** The object used to load and save ROIs. */
	private XMLFileIOStrategy			ioStrategy;
		
	/** Show the measurement units. */
	private MeasurementUnits			units;
	
	/**
     * Helper method to set the attributes of the newly created figure.
     * 
     * @param fig The figure to handle.
     */
    private void setFigureAttributes(ROIFigure fig)
    {
    	AttributeKeys.FONT_SIZE.set(fig, FONT_SIZE);
		AttributeKeys.TEXT_COLOR.set(fig, TEXT_COLOR);
		AttributeKeys.STROKE_WIDTH.set(fig, STROKE_WIDTH);
		MeasurementAttributes.SHOWMEASUREMENT.set(fig, false);
		MeasurementAttributes.MEASUREMENTTEXT_COLOUR.set(fig, MEASUREMENT_COLOR);
		DrawingAttributes.SHOWTEXT.set(fig, false);
    	if (fig instanceof MeasurePointFigure) {
    		AttributeKeys.FILL_COLOR.set(fig, FILL_COLOR_ALPHA);
    		AttributeKeys.STROKE_COLOR.set(fig, STROKE_COLOR_ALPHA);
    	} else {
    		AttributeKeys.FILL_COLOR.set(fig, FILL_COLOR);
    		AttributeKeys.STROKE_COLOR.set(fig, STROKE_COLOR);
    	}
    }
    
    /**
     * Helper method to set the annotations of the newly created shape.
     * 
     * @param shape The shape to handle.
     */
    private void setShapeAnnotations(ROIShape shape)
	{
    	ROIFigure fig = shape.getFigure();
		String type = fig.getType();
	//	if (type != null) AnnotationKeys.FIGURETYPE.set(shape, type);
		
		ROIShape s = fig.getROIShape();
	}
    
	/** 
	 * Creates a new instance. Initializes an collection to keep 
	 * track of the existing ROI.
	 */
	public ROIComponent()
	{
		roiCollection = new ROICollection();
		units = new MeasurementUnits(0, 0, 0 , false);
	}

	/**
	 * Removes the specified figure from the display.
	 * 
	 * @param figure The figure to remove.
	 * @throws NoSuchROIException If the ROI does not exist. 
	 */
	void removeROI(ROIFigure figure) throws NoSuchROIException
	{
		if (figure == null) return;
		long id = figure.getROI().getID();
		Coord3D coord = figure.getROIShape().getCoord3D();
		deleteShape(id, coord);	
	}

	/**
	 * Creates a <code>ROI</code> from the passed figure.
	 * 
	 * @param figure The figure to create the <code>ROI</code> from.
	 * @param currentPlane The plane to add figure to. 
	 * @return Returns the created <code>ROI</code>.
	 * @throws ROICreationException If the ROI cannot be created.
	 * @throws NoSuchROIException If the ROI does not exist.
	 */
	ROI createROI(ROIFigure figure, Coord3D currentPlane)
		throws ROICreationException, NoSuchROIException
	{
		ROI roi = createROI();
		ROIShape newShape = new ROIShape(roi, currentPlane, figure, 
							figure.getBounds());
		addShape(roi.getID(), currentPlane, newShape);
		return roi;
	}
	
	/**
	 * Sets the number of microns per pixel in the x-axis. 
	 * 
	 * @param x see above.
	 */
	public void setMicronsPixelX(double x) { units.setMicronsPixelX(x); }

	/**
	 * Returns the number of microns per pixel in the x-axis. 
	 * 
	 * @return microns see above.
	 */
	public double getMicronsPixelX() { return units.getMicronsPixelX(); }
	
	/**
	 * Sets the number of microns per pixel in the y-axis. 
	 * 
	 * @param y The value to set.
	 */
	public void setMicronsPixelY(double y) { units.setMicronsPixelY(y); }
	
	/**
	 * Returns the number of microns per pixel in the y-axis.
	 *  
	 * @return See above.
	 */
	public double getMicronsPixelY() { return units.getMicronsPixelY(); }
	
	/**
	 * Sets the number of microns per pixel in the z-axis. 
	 * 
	 * @param z The value to set.
	 * 
	 */
	public void setMicronsPixelZ(double z) { units.setMicronsPixelZ(z); }
	
	/**
	 * Returns the number of microns per pixel in the z-axis. 
	 * 
	 * @return See above.
	 */
	public double getMicronsPixelZ() { return units.getMicronsPixelZ(); }

    /**
     * Adds the specified figure to the display.
     * 
     * @param figure The figure to add.
     * @param currentPlane The plane to add figure to.
     * @return returns the newly created ROI. 
     * @throws NoSuchROIException 
     * @throws ROICreationException 
     */
    public ROI addROI(ROIFigure figure, Coord3D currentPlane)
    	throws ROICreationException, NoSuchROIException
    {
    	if (figure == null) throw new NullPointerException("Figure param null.");
    	setFigureAttributes(figure);
    	ROI roi = null;
    	roi = createROI(figure, currentPlane);
		if (roi == null) throw new ROICreationException("Unable to create ROI.");
    	ROIShape shape = figure.getROIShape();
    	setShapeAnnotations(shape);
    //	AnnotationKeys.ROIID.set(shape, roi.getID());
    	return roi;
    }
    
    /**
     * Adds the specified figure to the display.
     * 
     * @param figure The figure to add.
     * @param currentPlane The plane to add figure to.
     * @param addAttribs add attributes 
     * @return returns the newly created ROI. 
     * @throws NoSuchROIException 
     * @throws ROICreationException 
     */
    public ROI addROI(ROIFigure figure, Coord3D currentPlane, boolean addAttribs)
    	throws ROICreationException, NoSuchROIException
    {
    	if (figure == null) throw new NullPointerException("Figure param null.");
      	figure.setMeasurementUnits(units);
        if(addAttribs)
        	setFigureAttributes(figure);
    	ROI roi = null;
    	roi = createROI(figure, currentPlane);
		if (roi == null) throw new ROICreationException("Unable to create ROI.");
    	ROIShape shape = figure.getROIShape();
    	setShapeAnnotations(shape);
    //	AnnotationKeys.ROIID.set(shape, roi.getID());
    	return roi;
    }
	
	/** 
	 * Saves the current ROI data to passed stream. 
	 * 
	 * @param output The output stream to write the ROI into.
	 * @throws ParsingException Thrown if an error occurs while creating the 
	 * 							xml element. 
	 */
	public void saveROI(OutputStream output) 
		throws ParsingException
	{
		if (output == null)
			throw new NullPointerException("No input stream specified.");
		if (ioStrategy == null) ioStrategy = new XMLFileIOStrategy();
		ioStrategy.write(output, this);
	}
	
	/**
	 * Loads the ROIs. This method should be invoked straight after creating
	 * the component.
	 * 
	 * 
	 * @param input The stream with the previously saved ROIs or 
	 * 				<code>null</code> if no ROIs previously saved.
	 * @return list of newly loaded ROI.
	 * @throws ParsingException				Thrown when an error occured
	 * 										while parsing the stream.
	 * @throws NoSuchROIException		 	Tried to access a ROI which does not
	 * 									   	Exist. In this case most likely 
	 * 										reason is that a 
	 * 										LineConnectionFigure tried
	 * 									   	to link to ROIShapes which have not 
	 * 									   	been created yet.
	 * @throws ROICreationException		 	Thrown while trying to create an 
	 * 										ROI.
	 */
	public List<ROI> loadROI(InputStream input) 
		throws NoSuchROIException, ParsingException, ROICreationException
				
	{
		if (input == null)
			throw new NullPointerException("No input stream specified.");
		if (ioStrategy == null) ioStrategy = new XMLFileIOStrategy();
		return ioStrategy.read(input, this);
	}

	/**
	 * Generates the next ID for a new ROI. This method will possibly be 
	 * replaced with a call to the database for the generation of an ROI id.
	 * 
	 * @return See above.
	 */
	public long getNextID()
	{
		return roiCollection.getNextID();
	}

	/**
	 * Creates a ROI with an ROI id == id. This method is called from the IO
	 * strategy to create a pre-existing ROI from file.
	 * 
	 * Note : if a ROI is created with the same ID the new ROI will replace the
	 * old one.
	 * 
	 * @param id The ROI id. 
	 * @return See above.
	 * @throws ROICreationException			If an error occured while creating 
	 * 									   	an ROI, basic assumption is this is 
	 * 									   	linked to memory issues.
	 */
	public ROI createROI(long id)
		throws ROICreationException
	{
		return roiCollection.createROI(id);
	}

	/**
	 * Create a new ROI, assign it an ROI from the getNextID call.
	 * 
	 * @return See above. 
	 * @throws ROICreationException	If an error occured while creating 
	 * 								an ROI, basic assumption is this is 
	 * 								linked to memory issues.
	 */
	public ROI createROI()
		throws 	ROICreationException
	{
		return roiCollection.createROI();
	}

	/**
	 * Returns the roiMap which is the TreeMap containing the ROI, ROI.id pairs. 
	 * It is an ordered Tree. 
	 * 
	 * @return See above.
	 */
	public TreeMap<Long, ROI>  getROIMap()
	{
		return roiCollection.getROIMap();
	}

	/**
	 * Returns the ROI with the id == id. 
	 * This is obtained by a searh of the ROIMap. 
	 * 
	 * @param id the ROI.id that is being requested.
	 * @return See above.
	 * @throws NoSuchROIException Thrown if a ROI.id does not exist.
	 */
	public ROI getROI(long id) 
		throws NoSuchROIException
	{
		return roiCollection.getROI(id);
	}

	/**
	 * Returns the RIOShape which is part of the ROI id, and exists on the plane
	 * coord. This method looks up the ROIIDMap (TreeMap) for the ROI with id 
	 * and then looks up that ROIs TreeMap for the ROIShape on the plane coord.
	 * 
	 * @param id 	The id of the ROI the ROIShape is a member of.
	 * @param coord The plane where the ROIShape sits.
	 * @return See Above.
	 * @throws NoSuchROIException	Thrown if a ROI.id does not exist.
	 */
	public ROIShape getShape(long id, Coord3D coord) 
	throws 	NoSuchROIException
	{
		return roiCollection.getShape(id, coord);
	}

	/** 
	 * Returns the list of ROIShapes which reside on the plane coord. 
	 * The ShapeList is an object which contains a TreeMap of the 
	 * ROIShapes and ROIId of those shapes.
	 * 
	 * 
	 * @param coord The selected plane.
	 * @return See above. 
	 * @throws NoSuchROIException Thrown if no shapes are on plane Coord.
	 */
	public ShapeList getShapeList(Coord3D coord) 
		throws NoSuchROIException
	{
		return roiCollection.getShapeList(coord);
	}

	/** 
	 * Deletes the ROI and all its ROIShapes from the system.
	 * 
	 * @param id The ROI to delete. 
	 * @throws NoSuchROIException	Thrown if the ROI to be deleted does not 
	 * 								exist.
	 */
	public void deleteROI(long id) 
		throws NoSuchROIException
	{
		roiCollection.deleteROI(id);
	}

	/** 
	 * Deletes the ROIShape from the ROI with id. 
	 * 
	 * @param id 	The ROI id inwhich the ROIShape is a member.
	 * @param coord	The plane on which the ROIShape resides. 
	 * @throws NoSuchROIException	Thrown if the ROI does not exist.
	 */ 
	public void deleteShape(long id, Coord3D coord) 	
	throws 	NoSuchROIException
	{
		roiCollection.deleteShape(id, coord);
	}

	/**
	 * Adds a ROIShape to the ROI.id at coord. The ROIShape should be created 
	 * before hand.
	 * 
	 * Note : if a shape already exist with ROI.id and Coord.coord it will
	 * be replaced with the new ROIShape. 
	 * Note : if the shape is inserted in multiple places the system will behave
	 * oddly. 
	 * 
	 * @param id 	The ROI id.
	 * @param coord The selected plane.
	 * @param shape The shape to add.
	 * @throws ROICreationException	Thrown if the ROIShape cannot be created.
	 * @throws NoSuchROIException	Thrown if the ROI, the ROIShape should be 
	 * 								added to, does not exist. 
	 */
	public 	void addShape(long id, Coord3D coord, ROIShape shape) 
		throws 	ROICreationException, NoSuchROIException
	{
		roiCollection.addShape(id, coord, shape);
	}	

	/**
	 * This method will create new versions of the ROIShape belonging to ROI.id
	 * on plane coord and propagate it from plane start to end. If the shape 
	 * exists on a plane between start and end it will not be overwritten.
	 *
	 * Note : iteration for planes occurs through z then t.
	 *
	 * @param id 			The ROI id.
	 * @param selectedShape The plane where shape is to be duplicated from.
	 * @param start 		The plane to propagate from.
	 * @param end 			The plane to propagate to.
	 * @throws NoSuchROIException	Thrown if ROI with id does not exist.
	 * @throws ROICreationException	Thrown if the ROI cannot be created.
	 */
	public void propagateShape(long id, Coord3D selectedShape, Coord3D start, 
			Coord3D end) 
		throws  ROICreationException, NoSuchROIException
	{
		roiCollection.propagateShape(id, selectedShape, start, end);
	}

	/**
	 * Deletes the ROIShape belonging to ROI.id from plane start to plane end.
	 * This method requires that the object belongs on all planes from start 
	 * to end, if it does not then it will throw an NoSuchShapeException.
	 * 
	 * Note : iteration for planes occurs through z then t.
	 * 
	 * @param id 	The ROI id.
	 * @param start The start plane.
	 * @param end 	The end plane.
	 * @throws NoSuchROIException Thrown if no such ROI exists.
	 */
	public void deleteShape(long id, Coord3D start, Coord3D end) 
		throws 	NoSuchROIException
	{
		roiCollection.deleteShape(id, start, end);
	}

	/**
	 * Add an ROIRelationship to the system, the ROIRelationship will be parsed
	 * to see what has to be setup to create relationships. 
	 * This is a relationship between ROIs. 
	 * 
	 * @param relationship The relation to add.
	 */
	public void addROIRelationship(ROIRelationship relationship)
	{
		roiCollection.addROIRelationship(relationship);
	}

	/**
	 * Adds an ROIShapeRelationship to the system, the ROIShapeRelationship will 
	 * be parsed to see what has to be setup to create relationships. This is a 
	 * relationship between ROIShapes. 
	 * 
	 * @param relationship The relation to add.
	 */
	public void addROIShapeRelationship(ROIShapeRelationship relationship)
	{
		roiCollection.addROIShapeRelationship(relationship);
	}

	/**
	 * Removes the ROIRelationship with id from the system.
	 * 
	 * @param relationship The id of the relationship being removed.
	 */
	public void removeROIRelationship(long relationship)
	{
		roiCollection.removeROIRelationship(relationship);
	}

	/**
	 * Removes the ROIShapeRelationship with id from the system.
	 * 
	 * @param relationship The id of the relationship being removed.
	 */
	public void removeROIShapeRelationship(long relationship)
	{
		roiCollection.removeROIShapeRelationship(relationship);
	}

	/**
	 * Return <code>true</code> if the ROIRelationship exists in the system,
	 * <code>false</code> otherwise.
	 * 
	 * @param relationship The id of the relationship.
	 * @return See above.
	 */
	 public boolean containsROIRelationship(long relationship)
	 {
		 return roiCollection.containsROIRelationship(relationship);
	 }

	 /**
	  * Returns <code>true</code> if the ROIShapeRelationship exists 
	  * in the system, <code>false</code> otherwise.
	  * 
	  * @param relationship The id of the relationship.
	  * @return See above.
	  */
	 public boolean containsROIShapeRelationship(long relationship)
	 {
		 return roiCollection.containsROIShapeRelationship(relationship);
	 }

	 /**
	  * Returns the ROIRelationships which relate to ROI with id.
	  * 
	  * @param roiID id to find relationships which belong to it.
	  * @return see above.
	  */
	 public ROIRelationshipList getROIRelationshipList(long roiID)
	 {
		 return roiCollection.getROIRelationshipList(roiID);
	 }

	 /**
	  * Returns the ROIShapeRelationships which relate to ROIShape with ROI id.
	  * 
	  * @param roiID id to find relationships which belong to it.
	  * @return See above.
	  */
	 public ROIShapeRelationshipList getROIShapeRelationshipList(long roiID)
	 {
		 return roiCollection.getROIShapeRelationshipList(roiID);
	 }

	 /** 
	  * Shows the measurements in the ROIFigures in microns, if the passed value
	  * is <code>true</code>, in pixels otherwise.
	  * 
	  * @param inMicrons The value to set.
	  */
	 public void showMeasurementsInMicrons(boolean inMicrons)
	 {
		units.setInMicrons(inMicrons);
	 }

	 /**
	  * Returns the measurement units of this component.
	  * 
	  * @return See above.
	  */
	 public MeasurementUnits getMeasurementUnits() { return units; }

}


