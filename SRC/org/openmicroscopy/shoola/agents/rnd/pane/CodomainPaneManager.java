/*
 * org.openmicroscopy.shoola.agents.rnd.pane.CodomainPaneManager
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.env.rnd.codomain.ContrastStretchingDef;
import org.openmicroscopy.shoola.env.rnd.codomain.PlaneSlicingDef;

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
class CodomainPaneManager
	implements ActionListener, ItemListener
{
	/** JButtons events. */
	private static final int			STRETCHING = 0;
	private static final int			SLICING = 1;	
	
	/** CheckBoxes events. */
	private static final int			RI = 0;
	private static final int			CS = 1; 
	private static final int			PS = 2; 
	
	private QuantumMappingManager		control;
	private CodomainPane				view;
	private ContrastStretchingDef		csDef;
	private PlaneSlicingDef				psDef;
	private ContrastStretchingDialog	csDialog;
	private PlaneSlicingDialog			psDialog;
	
	
	CodomainPaneManager(CodomainPane view, QuantumMappingManager control,
						ContrastStretchingDef csDef, PlaneSlicingDef psDef)
	{
		this.view = view;
		this.control = control;
		this.csDef = csDef;
		this.psDef = psDef;
	}

	void attachListeners()
	{
		//CheckBox
		JCheckBox ri = view.getRI(), cs = view.getCS(), ps = view.getPS();
		ri.addItemListener(this);
		ri.setActionCommand(""+RI);
		cs.addItemListener(this);
		cs.setActionCommand(""+CS);
		ps.addItemListener(this);
		ps.setActionCommand(""+PS);
		
		//buttons
		JButton stretchingButton = view.getCStretching(),
				slicingButton = view.getPSlicing();
		stretchingButton.addActionListener(this);
		stretchingButton.setActionCommand(""+STRETCHING);
		slicingButton.addActionListener(this);
		slicingButton.setActionCommand(""+SLICING);	
	}

	/** Handles events fired by the JButtons. */
		public void actionPerformed(ActionEvent e)
		{
			String s = (String) e.getActionCommand();
			try {
				int     index = Integer.parseInt(s);
				switch (index) { 
					case STRETCHING:
						popUpContrastStretchingDialog();
						break;
					case SLICING:
						popUpPlaneSlicingDialog();
				}// end switch  
			} catch(NumberFormatException nfe) {
				//impossible if IDs are set correctly 
				throw nfe;  //just to be on the safe side...
			} 
		}

	/** Handle event fired by the CheckBox. */
	public void itemStateChanged(ItemEvent e)
	{
		//TODO: implement event fired by chech box.
		//register the event in the codomain chain 
		JCheckBox cb = (JCheckBox) e.getItemSelectable();
		if (e.getStateChange()== ItemEvent.DESELECTED) cb.setSelected(false);
	}
	
	/** Initializes the dialog window if it hasn't been created. */
	private void popUpContrastStretchingDialog()
	{
		if (csDialog == null)
			csDialog = new ContrastStretchingDialog(control, csDef);
		csDialog.setVisible(true);
	}
	
	/** Initializes the dialog window if it hasn't been created. */
	private void popUpPlaneSlicingDialog()
	{
		if(psDialog == null)
			psDialog = new PlaneSlicingDialog(control, psDef);
		psDialog.setVisible(true);
	}
	
}
