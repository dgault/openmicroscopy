/*
 * org.openmicroscopy.shoola.agents.rnd.pane.QuantumPaneManager
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
import javax.swing.JFrame;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.agents.rnd.RenderingAgtCtrl;
import org.openmicroscopy.shoola.env.config.Registry;
import org.openmicroscopy.shoola.env.rnd.codomain.CodomainMapContext;
import org.openmicroscopy.shoola.env.rnd.defs.QuantumDef;
import org.openmicroscopy.shoola.env.rnd.metadata.PixelsStatsEntry;
import org.openmicroscopy.shoola.env.rnd.quantum.QuantumFactory;
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
public class QuantumPaneManager
{	
	
	/** Reference to the {@link QuantumPane view}. */
	private QuantumPane			view;
	
	/** Reference to the {@link RenderingAgtCtrl eventManager}. */
	private RenderingAgtCtrl	eventManager;
	
	public QuantumPaneManager(RenderingAgtCtrl eventManager, QuantumPane view)
	{
		this.eventManager = eventManager;
		this.view = view;
	}
	
	/** Dispose all the dialog window. */
	public void disposeDialogs()
	{
		DomainPaneManager dpm = view.getDomainPane().getManager();
		dpm.disposeDialogs();
	}
	
	/** Reset the rendering defaults.update the GUI*/
	public void resetDefaults()
	{
		//DomainPane+Histogram
		DomainPaneManager dpm = view.getDomainPane().getManager();
		dpm.resetDefaults();
		
		//Codomain
		CodomainPaneManager cpm = view.getCodomainPane().getManager();
		cpm.resetDefaults();
		
		//Graphics representation
		view.getGRPane().removeAll();
		GraphicsRepresentation gr = view.getGRepresentation();
		gr = null;
		int mini = (int) eventManager.getGlobalChannelWindowStart(0);
		int maxi = (int) eventManager.getGlobalChannelWindowEnd(0);
		gr = new GraphicsRepresentation(this, QuantumFactory.LINEAR, 1.0, 0, 
										255, mini, maxi);
		gr.setReverseIntensity(false);
		gr.setDefaultLinear(mini, maxi);
		view.setGRepresentation(gr);
		view.buildGRPane();
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	Registry getRegistry() { return eventManager.getRegistry(); }
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	QuantumDef getQuantumDef() { return eventManager.getQuantumDef(); }
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getCodomainStart() { return eventManager.getCodomainStart(); }
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getCodomainEnd() { return eventManager.getCodomainEnd(); }
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getGlobalMinimum()
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		return (int) eventManager.getGlobalChannelWindowStart(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getGlobalMaximum()
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		return (int) eventManager.getGlobalChannelWindowEnd(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getGlobalChannelWindowStart(int w)
	{
		return (int) eventManager.getGlobalChannelWindowStart(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getGlobalChannelWindowEnd(int w)
	{
		return (int) eventManager.getGlobalChannelWindowEnd(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	PixelsStatsEntry[] getChannelStats(int w)
	{
		return eventManager.getChannelStats(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getChannelWindowStart(int w)
	{
		return (int) eventManager.getChannelWindowStart(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	int getChannelWindowEnd(int w)
	{
		return (int) eventManager.getChannelWindowEnd(w);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	void setCodomainLowerBound(int x)
	{
		eventManager.setCodomainLowerBound(x);
	}
	
	/** Forward event to {@link RenderingAgtCtrl}. */
	void setCodomainUpperBound(int x)
	{
		eventManager.setCodomainUpperBound(x);
	}
	
	/**
	 * Add or remove the codomain map context. 
	 * @param ctx		context of the codomain map.
	 * @param selected	<code>true</code> if the map is selected.			
	 * @param id		Action command id one the constant defined by 
	 * 					{@link CodomainPaneManager}. 
	 */
	void setCodomainMap(CodomainMapContext ctx, boolean selected, int id)
	{
		if (id == CodomainPaneManager.RI) updateGraphic(selected);
		if (selected) eventManager.addCodomainMap(ctx);
		else eventManager.removeCodomainMap(ctx);
	}
	
	/** Update the context. */
	void updateCodomainMap(CodomainMapContext ctx)
	{
		eventManager.updateCodomainMap(ctx);
	}
	
	/** 
	 * Forward event to @see RenderingAgtCtrl#setStrategy
	 *
	 * @param k				gamma	(real value).
	 * @param family 		one of the contants used to identify the family.
	 * @param resolution	bitResolution.
	 * @param id			Action command id, one the constant defined by 
	 * 						{@link DomainPaneManager}.
	 */
	void setQuantumStrategy(double k, int family, int resolution, int id)
	{
		if (id == DomainPaneManager.FAMILY)	
			updateGraphic(family);
		else if (id == DomainPaneManager.GAMMA)
			updateGraphic((int) (k*10), family);	//for graphic *10
		eventManager.setQuantumStrategy(k, family, resolution);
	}
	
	/**
	 * Select a new wavelength.
	 * @param w		wavelength index.
	 */
	void setWavelength(int w)
	{
		view.getGRPane().removeAll();
		GraphicsRepresentation gr = view.getGRepresentation();
		gr = null;
		int mini = (int) eventManager.getGlobalChannelWindowStart(w);
		int maxi = (int) eventManager.getGlobalChannelWindowEnd(w);
		int s = 
			(int) eventManager.getChannelWindowStart(w);
		int e = 
			(int) eventManager.getChannelWindowEnd(w);
		QuantumDef qDef = getQuantumDef();
		gr = new GraphicsRepresentation(this, qDef.family, 
				qDef.curveCoefficient, qDef.cdStart, qDef.cdEnd, mini, maxi);
		gr.setReverseIntensity(view.getCodomainPane().getRI().isSelected());
		if (qDef.family == QuantumFactory.EXPONENTIAL)
			gr.setDefaultExponential(s, e);
		else gr.setDefaultLinear(s, e);
		view.setGRepresentation(gr);
		view.buildGRPane();
		eventManager.setMappingPane();
	}
	
	/** 
	 * Resize the input window and synchronize the different views.
	 *
	 * @param value		real input value in the range [inputStart, inputEnd].
	 * @param w			wavelength index.
	 */
	void setInputWindowStart(int value, int w)
	{
		DomainPaneManager dpManager = view.getDomainPane().getManager();
		GraphicsRepresentationManager 
			grManager = view.getGRepresentation().getManager();
		dpManager.setInputWindowStart(value);
		
		grManager.setInputWindowStart(value, getGlobalChannelWindowStart(w),
									getGlobalChannelWindowEnd(w));
	}
	
	void setInputWindowStart(int value) 
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		setInputWindowStart(value, w);
	}
	
	/** Forward an event when the knob is released. */
	void setChannelWindowStart(int value)
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		eventManager.setChannelWindowStart(w, value);
	}
	
	/** 
	 * Resize the window input and synchronize the different views.
	 *
	 * @param value		real input value in the range [inputStart, inputEnd].
	 * @param w			wavelength index.
	 */
	void setInputWindowEnd(int value, int w)
	{
		DomainPaneManager dpManager = view.getDomainPane().getManager();
		GraphicsRepresentationManager 
			grManager = view.getGRepresentation().getManager();
		dpManager.setInputWindowEnd(value);
		grManager.setInputWindowEnd(value, getGlobalChannelWindowStart(w),
									getGlobalChannelWindowEnd(w));	
	}
	
	void setInputWindowEnd(int value) 
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		setInputWindowEnd(value, w);
	}
	
	/** Forward an event when the knob is released. */
	void setChannelWindowEnd(int value)
	{
		int w = view.getDomainPane().getWavelengths().getSelectedIndex();
		eventManager.setChannelWindowEnd(w, value);
	}
	
	/** Retrieve the main Frame. */
	JFrame getReferenceFrame()
	{
		return (JFrame) eventManager.getRegistry().getTopFrame().getFrame();
	}

	RenderingAgtCtrl getEventManager() { return eventManager; }
	
	/** Update the graphic. */
	private void updateGraphic(int coefficient, int family)
	{
		if (family == QuantumFactory.POLYNOMIAL) 
			view.getGRepresentation().setControlLocation(coefficient);
		else if (family == QuantumFactory.EXPONENTIAL) 
			view.getGRepresentation().setControlAndEndLocation(coefficient);
	}

	/** Update the graphic. */
	private void updateGraphic(int family) 
	{
		view.getGRepresentation().setControlsPoints(family);
		if (family == QuantumFactory.LOGARITHMIC)
			view.getGRepresentation().setControlLocation(
										GraphicsRepresentation.MIN);
		else if (family == QuantumFactory.LINEAR || 
				family == QuantumFactory.POLYNOMIAL)
			view.getGRepresentation().setControlLocation(
										GraphicsRepresentation.INIT);
		else if (family == QuantumFactory.EXPONENTIAL)
			view.getGRepresentation().setControlAndEndLocation(
										GraphicsRepresentation.INIT);
	}

	/** Update the graphic. */
	private void updateGraphic(boolean b)
	{
		view.getGRepresentation().reverse(b);
	}
	
}
