/*
 * org.openmicroscopy.shoola.agents.rnd.pane.GraphicsRepresentationManager
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

package org.openmicroscopy.shoola.agents.rnd.pane;

//Java imports
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.rnd.quantum.QuantumFactory;

/** 
 * Handles events fired the graphical cursors drawn in
 * {@link GraphicsRepresentation}.
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
class GraphicsRepresentationManager
	implements MouseListener, MouseMotionListener
{
	/** Graphical constant. */
	private static final int	topBorder = GraphicsRepresentation.topBorder, 
								leftBorder = GraphicsRepresentation.leftBorder,
								square = GraphicsRepresentation.square, 
								bottomBorder = 
									GraphicsRepresentation.bottomBorder,
								triangleW = GraphicsRepresentation.triangleW, 
	   							lS = leftBorder+square, tS = topBorder+square,
								length = 2*triangleW, absMin = leftBorder+20;
							
	private boolean                 dragging;
	private Rectangle               boxStart, boxEnd, boxOutputStart,
									boxOutputEnd;
	private int                     maxStartX, minEndX, maxEndX, 
									maxStartOutputY, minEndOutputY;

	private QuantumMappingManager	control;
	private GraphicsRepresentation	view;
	private int 					type;
	
	GraphicsRepresentationManager(GraphicsRepresentation view, 
									QuantumMappingManager control, 
									int type)
	{
		this.view = view;
		this.control = control;
		this.type = type;
		boxStart = new Rectangle();
		boxEnd = new Rectangle();
		boxOutputStart = new Rectangle();
		boxOutputEnd = new Rectangle();
		maxEndX = leftBorder+square/2; //only used if type = exponential
		attachListeners();
	}
	
	/** Attach listeners. */
	void attachListeners()
	{
		view.addMouseListener(this);
		view.addMouseMotionListener(this);
	}
	
	/**
	 * Resize the input window.
	 * The method is called by the control {@link QuantumMappingManager}.
	 * 
	 * @param value	real input value.
	 */
	void setInputWindowStart(int v)
	{
		int min = control.getMinimum();
		int vg = convertRealIntoGraphics(v, control.getMaximum()-min, 
										view.getInputGraphicsRange(), min);
		vg = vg + leftBorder;
		setInputStartBox(vg);
		view.updateInputStart(vg, v);	
	}

	/**
	 * Resize the input window.
	 * The method is called by the control {@link QuantumMappingManager}.
	 * 
	 * @param v	real input value.
	 */
	void setInputWindowEnd(int v)
	{
		int min = control.getMinimum();
		int vg = convertRealIntoGraphics(v, control.getMaximum()-min, 
										view.getInputGraphicsRange(), min);
		vg = vg + leftBorder;
		setInputEndBox(vg);
		view.updateInputEnd(vg, v);
	}
	
	void setInputRectangles(int start, int end)
	{
		setInputStartBox(start);
		setInputEndBox(end);
	}
	
	void setOutputRectangles(int start, int end)
	{
		setOutputStartBox(start);
		setOutputEndBox(end);
	}
	
	/** 
	 * Converts a graphic value into a real value 
	 * (equation of the form y=ax+b).
	 * The rangeReal & rangeGraphics values are used to 
	 * compute a; b is a parameter.
	 *
	 * @param x             x-coordinate (graphic).
	 * @param rangeReal     real range. 
	 * @param rangeGraphics graphic range.
	 * @param b				equation's parameter.
	 * @return a real value	
	 */    
    int convertGraphicsIntoReal(int x, int rangeReal, int rangeGraphics, int b)
    {
        double a = (double) rangeReal/rangeGraphics;
        return (int) (a*x+b);
    }
    
	/** 
	 * Converts a real value into a graphic value 
	 * (equation of the form y=ax+b).
	 * The rangeReal & rangeGraphics values are used to compute a;
	 * b is a parameter.
	 *
	 * @param x             x-coordinate (real)
	 * @param rangeReal     real range. 
	 * @param rangeGraphics graphic range.
	 * @param b				equation's parameter
	 * @return a graphic value
	 */    
    int convertRealIntoGraphics(int x, int rangeReal, int rangeGraphics, int b)
    {
        double a = (double) rangeGraphics/rangeReal;
        return (int) (a*(x-b));
    }
	
	/** Handles events fired the cursors. */
	public void mousePressed(MouseEvent e)
	{
		Point p = e.getPoint();
		if (!dragging) {
			dragging = true;
			if (boxEnd.contains(p) && p.x >= leftBorder && p.x <= lS && 
				p.x >= maxStartX && p.x <= maxEndX && p.x >= absMin 
				&& type == QuantumFactory.EXPONENTIAL ) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);	
				control.setInputWindowEnd(v);
			}
			if (boxEnd.contains(p) && p.x >= leftBorder && p.x <= lS &&
				p.x >= maxStartX && type != QuantumFactory.EXPONENTIAL) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);
				control.setInputWindowEnd(v);
			}
				
			if (boxStart.contains(p) && p.x >= leftBorder && p.x <= lS &&
				p.x <= minEndX) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);	
				control.setInputWindowStart(v);
			}
				
			if (boxOutputStart.contains(p) && p.y >= minEndOutputY &&
				p.y <= tS)
				setOutputWindowStart(p.y);	
			if (boxOutputEnd.contains(p) && p.y <= maxStartOutputY &&
				p.y >= topBorder)
				setOutputWindowEnd(p.y);
		 }  //else dragging already in progress 
	}
	
	/** Handles events fired the cursors. */    
	public void mouseDragged(MouseEvent e)
	{
		Point   p = e.getPoint();
		if (dragging) {
			if (boxEnd.contains(p) && p.x >= leftBorder && p.x <= lS && 
				p.x >= maxStartX && p.x <= maxEndX && p.x >= absMin 
				&& type == QuantumFactory.EXPONENTIAL ) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);	
				control.setInputWindowEnd(v);
			}
			if (boxEnd.contains(p) && p.x >= leftBorder && p.x <= lS &&
				p.x >= maxStartX && type != QuantumFactory.EXPONENTIAL) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);	
				control.setInputWindowEnd(v);
			}
			if (boxStart.contains(p) && p.x >= leftBorder && p.x <= lS &&
				p.x <= minEndX) {
				int min = control.getMinimum();
				int v = convertGraphicsIntoReal(
								p.x-leftBorder, control.getMaximum()-min, 
								view.getInputGraphicsRange(), min
												);
				control.setInputWindowStart(v);	
			}	
			if (boxOutputStart.contains(p) && p.y >= minEndOutputY 
				&& p.y <= tS)
				setOutputWindowStart(p.y);
			if (boxOutputEnd.contains(p) && p.y <= maxStartOutputY && 
				p.y >= topBorder)
				setOutputWindowEnd(p.y);
		}
	}
	
	/** Resets the dragging control to false. */    
	public void mouseReleased(MouseEvent e)
	{
		dragging = false;
	}
	
	/**
	 * Resize the output window.
	 * 
	 * @param y		graphics value.
	 */
	void setOutputWindowStart(int y)
	{
		setOutputStartBox(y);
		view.updateOutputStart(y);
		int yReal = convertGraphicsIntoReal(y, 255, square, 0);
	}

	/**
	 * Resize the output window.
	 * 
	 * @param value		graphics value..
	 */
	void setOutputWindowEnd(int y)
	{
		setOutputEndBox(y);
		view.updateOutputEnd(y);
		int yReal = convertGraphicsIntoReal(y, 255, square, 0);
	}
	
	/** 
	 * Sets the type. 
	 *
	 * @param t     family index.
	 * @param x     MaxEndX value.
	 */
	void setType(int type, int x)
	{
		this.type = type;
		maxEndX = x ;
	}
	
	/** 
	 * Sets  the MaxEndX value that is used to control the cursors' motions.
	 *
	 * @param x value.
	 */    
	void setMaxEndX(int x)
	{
		 maxEndX = x ;
	}
	
	/** 
	 * Sizes the rectangle used to listen to the outpuStart cursor.
	 *
	 * @param y     y-coordinate.
	 */ 
	private void setOutputStartBox(int y)
	{
		maxStartOutputY = y-triangleW;
		boxOutputStart.setBounds(0, y-triangleW, leftBorder-triangleW-1,
										 length);
	}
	
	/** 
	 * Sizes the rectangle used to listen to the outputEnd cursor.
	 *
	 * @param y     y-coordinate.
	 */  
	private void setOutputEndBox(int y)
	{
		minEndOutputY = y+triangleW;
		boxOutputEnd.setBounds(0, y-triangleW, leftBorder-triangleW-1, length);
	}
	
	/** 
	 * Sizes the rectangle used to listen to the inputStart cursor.
	 *
	 * @param x     x-coordinate.
	 */
	void setInputStartBox(int x)
	{
		maxStartX = x+triangleW;
		boxStart.setBounds(x-triangleW, tS+triangleW+1, length, bottomBorder);
	}
	
	/** 
	 * Sizes the rectangle used to listen to the inputEnd cursor.
	 *
	 * @param x     x-coordinate.
	 */  
	void setInputEndBox(int x)
	{
		minEndX = x-triangleW;
		boxEnd.setBounds(x-triangleW, tS+triangleW+1, length, bottomBorder);
	}
	
	/** 
	 * Required by I/F but not actually needed in our case, no op 
	 * implementation.
	 */   
	public void mouseMoved(MouseEvent e) {}
	
	/** 
	 * Required by I/F but not actually needed in our case, no op 
	 * implementation.
	 */    
	public void mouseClicked(MouseEvent e) {}
	
	/** 
	 * Required by I/F but not actually needed in our case, no op 
	 * implementation.
	 */   
	public void mouseEntered(MouseEvent e) {}
	
	/** 
	 * Required by I/F but not actually needed in our case, no op 
	 * implementation.
	 */    
	public void mouseExited(MouseEvent e) {}
	
}
