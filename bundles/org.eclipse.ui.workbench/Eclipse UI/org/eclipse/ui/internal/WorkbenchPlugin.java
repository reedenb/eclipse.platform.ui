/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.internal;

import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.util.Policy;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.decorators.DecoratorManager;
import org.eclipse.ui.internal.intro.IIntroRegistry;
import org.eclipse.ui.internal.intro.IntroRegistry;
import org.eclipse.ui.internal.intro.IntroRegistryReader;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.internal.registry.ActionSetRegistry;
import org.eclipse.ui.internal.registry.EditorRegistry;
import org.eclipse.ui.internal.registry.IViewRegistry;
import org.eclipse.ui.internal.registry.PerspectiveRegistry;
import org.eclipse.ui.internal.registry.PreferencePageRegistryReader;
import org.eclipse.ui.internal.registry.ViewRegistry;
import org.eclipse.ui.internal.registry.ViewRegistryReader;
import org.eclipse.ui.internal.registry.WorkingSetRegistry;
import org.eclipse.ui.internal.themes.IThemeRegistry;
import org.eclipse.ui.internal.themes.ThemeRegistry;
import org.eclipse.ui.internal.themes.ThemeRegistryReader;
import org.eclipse.ui.internal.util.BundleUtility;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.presentations.AbstractPresentationFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * This class represents the TOP of the workbench UI world
 * A plugin class is effectively an application wrapper
 * for a plugin & its classes. This class should be thought
 * of as the workbench UI's application class.
 *
 * This class is responsible for tracking various registries
 * font, preference, graphics, dialog store.
 *
 * This class is explicitly referenced by the 
 * workbench plugin's  "plugin.xml" and places it
 * into the UI start extension point of the main
 * overall application harness
 *
 * When is this class started?
 *      When the Application
 *      calls createExecutableExtension to create an executable
 *      instance of our workbench class.
 */
public class WorkbenchPlugin extends AbstractUIPlugin {
	// Default instance of the receiver
	private static WorkbenchPlugin inst;
	// Manager that maps resources to descriptors of editors to use
	private EditorRegistry editorRegistry;
	// Manager for the DecoratorManager
	private DecoratorManager decoratorManager;
	// Theme registry
	private ThemeRegistry themeRegistry;
	// Manager for working sets (IWorkingSet)
	private WorkingSetManager workingSetManager;
	// Working set registry, stores working set dialogs
	private WorkingSetRegistry workingSetRegistry;	
	// The context within which this plugin was started.
	private BundleContext bundleContext;

	/**
	 * Global workbench ui plugin flag. Only workbench implementation is allowed to use this flag
	 * All other plugins, examples, or test cases must *not* use this flag.
	 */ 
	public static boolean DEBUG = false;

	/**
	 * The workbench plugin ID.
	 * 
	 * @issue we should just drop this constant and use PlatformUI.PLUGIN_ID instead
	 */
	public static String PI_WORKBENCH = PlatformUI.PLUGIN_ID;

	/**
	 * The character used to separate preference page category ids
	 */
	private static char PREFERENCE_PAGE_CATEGORY_SEPARATOR = '/';

	// Other data.
	private PreferenceManager preferenceManager;
	private ViewRegistry viewRegistry;
	private PerspectiveRegistry perspRegistry;
	private ActionSetRegistry actionSetRegistry;
	private SharedImages sharedImages;
	
	/**
	 * Information describing the product (formerly called "primary plugin"); lazily
	 * initialized.
	 * @since 3.0
	 */
	private ProductInfo productInfo = null;
	private IntroRegistry introRegistry;
	
	/**
	 * Create an instance of the WorkbenchPlugin. The workbench plugin is
	 * effectively the "application" for the workbench UI. The entire UI
	 * operates as a good plugin citizen.
	 */
	public WorkbenchPlugin() {
		super();
		inst = this;
	}

	/**
	 * Unload all members.  This can be used to run a second instance of a workbench.
	 * @since 3.0 
	 */
	void reset() {
		editorRegistry = null;

        if (decoratorManager != null) {
            decoratorManager.dispose();
            decoratorManager = null;
        }

		themeRegistry = null;
		workingSetManager = null;
		workingSetRegistry = null;	

		preferenceManager = null;
		viewRegistry = null;
		perspRegistry = null;
		actionSetRegistry = null;
		sharedImages = null;

		productInfo = null;
		introRegistry = null;

		DEBUG = false;
	}

	/**
	 * Creates an extension.  If the extension plugin has not
	 * been loaded a busy cursor will be activated during the duration of
	 * the load.
	 *
	 * @param element the config element defining the extension
	 * @param classAttribute the name of the attribute carrying the class
	 * @return the extension object
	 * @throws CoreException if the extension cannot be created
	 */
	public static Object createExtension(final IConfigurationElement element, final String classAttribute) throws CoreException {
		try {
			// If plugin has been loaded create extension.
			// Otherwise, show busy cursor then create extension.
			if(BundleUtility.isActivated(element.getDeclaringExtension().getNamespace())) {
				return element.createExecutableExtension(classAttribute);
			} 
			final Object[] ret = new Object[1];
			final CoreException[] exc = new CoreException[1];
			BusyIndicator.showWhile(null, new Runnable() {
				public void run() {
					try {
						ret[0] = element.createExecutableExtension(classAttribute);
					} catch (CoreException e) {
						exc[0] = e;
					}
				}
			});
			if (exc[0] != null)
				throw exc[0];
			return ret[0];
			
		}
		catch (CoreException core) {
			throw core;			
		}
		catch (Exception e) {
			throw new CoreException(
				new Status(
						IStatus.ERROR, 
						PI_WORKBENCH, 
						IStatus.ERROR, 
						WorkbenchMessages.getString("WorkbenchPlugin.extension"), //$NON-NLS-1$ 
						e));
		}
	}
	/**
	 * Returns the image registry for this plugin.
	 *
	 * Where are the images?  The images (typically gifs) are found in the 
	 * same plugins directory.
	 *
	 * @see ImageRegistry
	 *
	 * Note: The workbench uses the standard JFace ImageRegistry to track its images. In addition 
	 * the class WorkbenchGraphicResources provides convenience access to the graphics resources 
	 * and fast field access for some of the commonly used graphical images.
	 */
	protected ImageRegistry createImageRegistry() {
		return WorkbenchImages.getImageRegistry();
	}
	
	/**
	 * Returns the action set registry for the workbench.
	 *
	 * @return the workbench action set registry
	 */
	public ActionSetRegistry getActionSetRegistry() {
		if (actionSetRegistry == null) {
			actionSetRegistry = new ActionSetRegistry();
		}
		return actionSetRegistry;
	}

	/**
	 * Return the default instance of the receiver. This represents the runtime plugin.
	 * @return WorkbenchPlugin
	 * @see AbstractUIPlugin for the typical implementation pattern for plugin classes.
	 */
	public static WorkbenchPlugin getDefault() {
		return inst;
	}
	/**
	 * Answer the manager that maps resource types to a the 
	 * description of the editor to use
	 * @return IEditorRegistry the editor registry used
	 * by this plug-in.
	 */

	public IEditorRegistry getEditorRegistry() {
		if (editorRegistry == null) {
			editorRegistry = new EditorRegistry();
		}
		return editorRegistry;
	}
	/**
	 * Answer the element factory for an id, or <code>null</code. if not found.
	 * @param targetID
	 * @return
	 */
	public IElementFactory getElementFactory(String targetID) {

		// Get the extension point registry.
		IExtensionPoint extensionPoint;
		extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(PI_WORKBENCH, IWorkbenchConstants.PL_ELEMENT_FACTORY);

		if (extensionPoint == null) {
			WorkbenchPlugin.log("Unable to find element factory. Extension point: " + IWorkbenchConstants.PL_ELEMENT_FACTORY + " not found"); //$NON-NLS-2$ //$NON-NLS-1$
			return null;
		}

		// Loop through the config elements.
		IConfigurationElement targetElement = null;
		IConfigurationElement[] configElements = extensionPoint.getConfigurationElements();
		for (int j = 0; j < configElements.length; j++) {
			String strID = configElements[j].getAttribute("id"); //$NON-NLS-1$
			if (targetID.equals(strID)) {
				targetElement = configElements[j];
				break;
			}
		}
		if (targetElement == null) {
			// log it since we cannot safely display a dialog.
			WorkbenchPlugin.log("Unable to find element factory: " + targetID); //$NON-NLS-1$
			return null;
		}

		// Create the extension.
		IElementFactory factory = null;
		try {
			factory = (IElementFactory) createExtension(targetElement, "class"); //$NON-NLS-1$
		} catch (CoreException e) {
			// log it since we cannot safely display a dialog.
			WorkbenchPlugin.log("Unable to create element factory.", e.getStatus()); //$NON-NLS-1$
			factory = null;
		}
		return factory;
	}
	
	/**
	 * Returns the presentation factory with the given id, or <code>null</code> if not found.
	 * @param targetID The id of the presentation factory to use.
	 * @return AbstractPresentationFactory or <code>null</code>
	 * if not factory matches that id.
	 */
	public AbstractPresentationFactory getPresentationFactory(String targetID) {
	    Object o = createExtension(IWorkbenchConstants.PL_PRESENTATION_FACTORIES, "factory", targetID); //$NON-NLS-1$
	    if (o instanceof AbstractPresentationFactory) {
	        return (AbstractPresentationFactory) o;
	    }
	    WorkbenchPlugin.log("Error creating presentation factory: " + targetID + " -- class is not an AbstractPresentationFactory"); //$NON-NLS-1$ //$NON-NLS-2$
		return null;
	}

	/**
	 * Looks up the configuration element with the given id on the given extension point
	 * and instantiates the class specified by the class attributes.
	 * 
	 * @param extensionPointId the extension point id (simple id)
	 * @param elementName the name of the configuration element, or <code>null</code>
	 *   to match any element
	 * @param targetID the target id
	 * @return the instantiated extension object, or <code>null</code> if not found
	 */
	private Object createExtension(String extensionPointId, String elementName, String targetID) {
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(PI_WORKBENCH, extensionPointId);
		if (extensionPoint == null) {
			WorkbenchPlugin.log("Unable to find extension. Extension point: " + extensionPointId + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}

		// Loop through the config elements.
		IConfigurationElement targetElement = null;
		IConfigurationElement[] elements = extensionPoint.getConfigurationElements();
		for (int j = 0; j < elements.length; j++) {
		    IConfigurationElement element = elements[j];
		    if (elementName == null || elementName.equals(element.getName())) {
		        String strID = element.getAttribute("id"); //$NON-NLS-1$
		        if (targetID.equals(strID)) {
		            targetElement = element;
		            break;
		        }
			}
		}
		if (targetElement == null) {
			// log it since we cannot safely display a dialog.
			WorkbenchPlugin.log("Unable to find extension: " + targetID //$NON-NLS-1$
                    + " in extension point: " + extensionPointId); //$NON-NLS-1$ 
			return null;
		}

		// Create the extension.
		try {
			return createExtension(targetElement, "class"); //$NON-NLS-1$
		} catch (CoreException e) {
			// log it since we cannot safely display a dialog.
			WorkbenchPlugin.log(
                    "Unable to create extension: " + targetID //$NON-NLS-1$
                            + " in extension point: " + extensionPointId //$NON-NLS-1$
                            + ", status: ", e.getStatus()); //$NON-NLS-1$
		}
		return null;
	}
	/**
	 * Return the perspective registry.
	 * @return IPerspectiveRegistry. The registry for the receiver.
	 */
	public IPerspectiveRegistry getPerspectiveRegistry() {
		if (perspRegistry == null) {
			perspRegistry = new PerspectiveRegistry();
			perspRegistry.load();
		}
		return perspRegistry;
	}
	/**
	 * Returns the working set manager
	 * 
	 * @return the working set manager
	 * @since 2.0
	 */
	public IWorkingSetManager getWorkingSetManager() {
		if (workingSetManager == null) {
			workingSetManager = new WorkingSetManager();
			workingSetManager.restoreState();			
		}
		return workingSetManager;
	}
	/**
	 * Returns the working set registry
	 * 
	 * @return the working set registry
	 * @since 2.0
	 */
	public WorkingSetRegistry getWorkingSetRegistry() {
		if (workingSetRegistry == null) {
			workingSetRegistry = new WorkingSetRegistry();
			workingSetRegistry.load();
		}
		return workingSetRegistry;
	}
	
	/**
	 * Returns the introduction registry.
     *
     * @return the introduction registry.
	 * @since 3.0
	 */
	public IIntroRegistry getIntroRegistry() {
		if (introRegistry == null) {
			introRegistry = new IntroRegistry();
			IntroRegistryReader reader = new IntroRegistryReader();
			reader.readIntros(Platform.getExtensionRegistry(), introRegistry);
		}
		return introRegistry;
	}
	
	/**
	 * Get the preference manager.
	 * @return PreferenceManager the preference manager for
	 * the receiver.
	 */
	public PreferenceManager getPreferenceManager() {
		if (preferenceManager == null) {
			preferenceManager = new PreferenceManager(PREFERENCE_PAGE_CATEGORY_SEPARATOR);

			//Get the pages from the registry
			PreferencePageRegistryReader registryReader = new PreferencePageRegistryReader(getWorkbench());
			List pageContributions = registryReader.getPreferenceContributions(Platform.getExtensionRegistry());

			//Add the contributions to the manager
			Iterator enum = pageContributions.iterator();
			while (enum.hasNext()) {
				preferenceManager.addToRoot((IPreferenceNode) enum.next());
			}
 		}
		return preferenceManager;
	}

	/**
	 * Returns the shared images for the workbench.
	 *
	 * @return the shared image manager
	 */
	public ISharedImages getSharedImages() {
		if (sharedImages == null)
			sharedImages = new SharedImages();
		return sharedImages;
	}

	/**
	 * Returns the theme registry for the workbench.
	 * 
	 * @return the theme registry
	 */
	public IThemeRegistry getThemeRegistry() {
		if (themeRegistry == null) {
			try {
				themeRegistry = new ThemeRegistry();
				ThemeRegistryReader reader = new ThemeRegistryReader();
				reader.readThemes(Platform.getExtensionRegistry(), themeRegistry);
			} catch (CoreException e) {
				// cannot safely show a dialog so log it
				WorkbenchPlugin.log("Unable to read theme registry.", e.getStatus()); //$NON-NLS-1$
			}
		}
		return themeRegistry;
	}
	
	/**
	 * Answer the view registry.
	 * @return IViewRegistry the view registry for the
	 * receiver.
	 */
	public IViewRegistry getViewRegistry() {
		if (viewRegistry == null) {
			viewRegistry = new ViewRegistry();
			try {
				ViewRegistryReader reader = new ViewRegistryReader();
				reader.readViews(Platform.getExtensionRegistry(), viewRegistry);
			} catch (CoreException e) {
				// cannot safely show a dialog so log it
				WorkbenchPlugin.log("Unable to read view registry.", e.getStatus()); //$NON-NLS-1$
			}
		}
		return viewRegistry;
	}
	/**
	 * Answer the workbench.
	 * @deprecated Use <code>PlatformUI.getWorkbench()</code> instead.
	 */
	public IWorkbench getWorkbench() {
		return PlatformUI.getWorkbench();
	}
	/** 
	 * Set default preference values.
	 * This method must be called whenever the preference store is initially loaded
	 * because the default values are not stored in the preference store.
	 */
	protected void initializeDefaultPreferences(IPreferenceStore store) {
	    // Do nothing.  This should not be called.
	    // Prefs are initialized in WorkbenchPreferenceInitializer.
	}

	/**
	 * Log the given status to the ISV log.
	 *
	 * When to use this:
	 *
	 *		This should be used when a PluginException or a
	 *		ExtensionException occur but for which an error
	 *		dialog cannot be safely shown.
	 *
	 *		If you can show an ErrorDialog then do so, and do
	 *		not call this method.
	 *
	 *		If you have a plugin exception or core exception in hand
	 *		call log(String, IStatus)
	 *
	 * This convenience method is for internal use by the Workbench only
	 * and must not be called outside the workbench.
	 *
	 * This method is supported in the event the log allows plugin related
	 * information to be logged (1FTTJKV). This would be done by this method.
	 *
	 * This method is internal to the workbench and must not be called
	 * by any plugins, or examples.
	 *
	 * @param message 	A high level UI message describing when the problem happened.
	 *
	 */

	public static void log(String message) {
		getDefault().getLog().log(StatusUtil.newStatus(IStatus.ERROR, message, null));
		System.err.println(message);
		//1FTTJKV: ITPCORE:ALL - log(status) does not allow plugin information to be recorded
	}
	/**
	 * Log the given status to the ISV log.
	 *
	 * When to use this:
	 *
	 *		This should be used when a PluginException or a
	 *		ExtensionException occur but for which an error
	 *		dialog cannot be safely shown.
	 *
	 *		If you can show an ErrorDialog then do so, and do
	 *		not call this method.
	 *
	 * This convenience method is for internal use by the workbench only
	 * and must not be called outside the workbench.
	 *
	 * This method is supported in the event the log allows plugin related
	 * information to be logged (1FTTJKV). This would be done by this method.
	 *
	 * This method is internal to the workbench and must not be called
	 * by any plugins, or examples.
	 *
	 * @param message 	A high level UI message describing when the problem happened.
	 *					May be null.
	 * @param status  	The status describing the problem.
	 *					Must not be null.
	 *
	 */

	public static void log(String message, IStatus status) {

		//1FTUHE0: ITPCORE:ALL - API - Status & logging - loss of semantic info

		if (message != null) {
			getDefault().getLog().log(StatusUtil.newStatus(IStatus.ERROR, message, null));
			System.err.println(message + "\nReason:"); //$NON-NLS-1$
		}

		getDefault().getLog().log(status);
		System.err.println(status.getMessage());

		//1FTTJKV: ITPCORE:ALL - log(status) does not allow plugin information to be recorded
	}
	
	/**
	 * @param aWorkbench the workbench for the receiver.
	 * @deprecated Use <code>PlatformUI.createAndRunWorkbench</code>.
	 */
	public void setWorkbench(IWorkbench aWorkbench) {
		// Do nothing
	}

	/**
	 * Get the decorator manager for the receiver
	 * @return DecoratorManager the decorator manager
	 * for the receiver.
	 */
	public DecoratorManager getDecoratorManager() {
		if (this.decoratorManager == null) {
			this.decoratorManager = new DecoratorManager();
			this.decoratorManager.restoreListeners();
		}
		return decoratorManager;
	}

	/*
	 *  (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
    public void start(BundleContext context) throws Exception {
    	super.start(context);
    	bundleContext = context;
    	Policy.setLog(getLog());
    	
    	// Start the UI plugin so that it can install the callback in PrefUtil,
    	// which needs to be done as early as possible, before the workbench
    	// accesses any API preferences.
    	Platform.getBundle(PlatformUI.PLUGIN_ID).start();
    }

    /**
     * Return an array of all bundles contained in this workbench.
     * 
     * @return an array of bundles in the workbench or an empty array if none
     * @since 3.0
     */
    public Bundle[] getBundles() {
        return bundleContext == null ? new Bundle[0] : bundleContext.getBundles();
    }

	/**
	 * Returns the application name.
	 * <p>
	 * Note this is never shown to the user.
	 * It is used to initialize the SWT Display.
	 * On Motif, for example, this can be used
	 * to set the name used for resource lookup.
	 * </p>
	 *
	 * @return the application name, or <code>null</code>
	 * @see org.eclipse.swt.widgets.Display#setAppName
	 * @since 3.0
	 */
	public String getAppName() {
		return getProductInfo().getAppName();
	}
	
	/**
	 * Returns the name of the product.
	 * 
	 * @return the product name, or <code>null</code> if none
	 * @since 3.0
	 */
	public String getProductName() {
		return getProductInfo().getProductName();
	}

	/**
	 * Returns the image descriptors for the window image to use for this product.
	 * 
	 * @return an array of the image descriptors for the window image, or
	 *         <code>null</code> if none
	 * @since 3.0
	 */
	public ImageDescriptor[] getWindowImages() {
		return getProductInfo().getWindowImages();
	}

	/**
	 * Returns an instance that describes this plugin's product (formerly "primary
	 * plugin").
	 * @return ProductInfo the product info for the receiver
	 */
	private ProductInfo getProductInfo() {
		if(productInfo == null)
			productInfo = new ProductInfo(Platform.getProduct());
		return productInfo;
 	}
}
