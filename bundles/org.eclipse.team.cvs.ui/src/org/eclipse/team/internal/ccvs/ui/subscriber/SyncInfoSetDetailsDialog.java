/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.subscriber;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ccvs.ui.AdaptableResourceList;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.team.internal.ui.dialogs.DetailsDialog;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * DetailsDialog that has a details area which shows the SyncInfos
 * in a SyncInfoSet.
 */
public abstract class SyncInfoSetDetailsDialog extends DetailsDialog {

	private static final int WIDTH_HINT = 350;
	private final static int SELECTION_HEIGHT_HINT = 100;
	
	private CheckboxTableViewer listViewer;
	
	private SyncInfoSet syncSet;
	private Object[] selectedResources;

	/**
	 * @param parentShell
	 * @param dialogTitle
	 */
	public SyncInfoSetDetailsDialog(Shell parentShell, String dialogTitle, String detailsTitle, SyncInfoSet syncSet) {
		super(parentShell, dialogTitle);
		this.syncSet = syncSet;
		//this.detailsTitle = detailsTitle;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.DetailsDialog#createDropDownDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Composite createDropDownDialogArea(Composite parent) {
		Composite composite = createComposite(parent);
				
		addResourcesArea(composite);
		
		// TODO: set F1 help
		//WorkbenchHelp.setHelp(composite, IHelpContextIds.ADD_TO_VERSION_CONTROL_DIALOG);
		
		return composite;
	}

	/**
	 * @param composite
	 */
	private void addResourcesArea(Composite composite) {
		//createWrappingLabel(composite, detailsTitle);
		// add the selectable checkbox list
		
		listViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.heightHint = SELECTION_HEIGHT_HINT;
		data.widthHint = WIDTH_HINT;
		listViewer.getTable().setLayoutData(data);

		// set the contents of the list
		listViewer.setLabelProvider(new WorkbenchLabelProvider() {
			protected String decorateText(String input, Object element) {
				if (element instanceof IResource)
					return ((IResource)element).getFullPath().toString();
				else
					return input;
			}
		});
		listViewer.setContentProvider(new WorkbenchContentProvider());
		setViewerInput();
		listViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				selectedResources = listViewer.getCheckedElements();
			}
		});
		
		addSelectionButtons(composite);
		
	}
	
	/**
	 * Add the selection and deselection buttons to the dialog.
	 * @param composite org.eclipse.swt.widgets.Composite
	 */
	private void addSelectionButtons(Composite composite) {
	
		Composite buttonComposite = new Composite(composite, SWT.RIGHT);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		buttonComposite.setLayout(layout);
		GridData data =
			new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL);
		data.grabExcessHorizontalSpace = true;
		composite.setData(data);
	
		Button selectButton = createButton(buttonComposite, IDialogConstants.SELECT_ALL_ID, Policy.bind("ReleaseCommentDialog.selectAll"), false); //$NON-NLS-1$
		SelectionListener listener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				listViewer.setAllChecked(true);
				selectedResources = null;
			}
		};
		selectButton.addSelectionListener(listener);
	
		Button deselectButton = createButton(buttonComposite, IDialogConstants.DESELECT_ALL_ID, Policy.bind("ReleaseCommentDialog.deselectAll"), false); //$NON-NLS-1$
		listener = new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				listViewer.setAllChecked(false);
				selectedResources = new Object[0];
	
			}
		};
		deselectButton.addSelectionListener(listener);
	}
	
	protected void setViewerInput() {
		if (listViewer == null || listViewer.getControl().isDisposed()) return;
		listViewer.setInput(new AdaptableResourceList(getAllResources()));
		if (selectedResources == null) {
			listViewer.setAllChecked(true);
		} else {
			listViewer.setCheckedElements(selectedResources);
		}
	}
	
	protected void resetViewerInput() {
		selectedResources = null;
		setViewerInput();
	}
	/**
	 * Return a list of all the resources that are currently under consideration by the dialog
	 */
	protected IResource[] getAllResources() {
		return syncSet.getResources();
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.DetailsDialog#updateEnablements()
	 */
	protected void updateEnablements() {
	}
	
	/**
	 * @return
	 */
	public SyncInfoSet getSyncSet() {
		return syncSet;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
	 */
	protected void buttonPressed(int id) {
		if (id == IDialogConstants.OK_ID) {
			filterSyncSet();
		}
		super.buttonPressed(id);
	}

	protected void filterSyncSet() {
		// Keep only the checked resources
		if (selectedResources != null) {
			getSyncSet().selectNodes(new FastSyncInfoFilter() {
				public boolean select(SyncInfo info) {
					IResource local = info.getLocal();
					for (int i = 0; i < selectedResources.length; i++) {
						if (local.equals(selectedResources[i])) return true;
					}
					return false;
				}
			});
		}
	}
}
