package org.eclipse.team.core;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IFileModificationValidator;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.team.core.internal.Policy;
import org.eclipse.team.internal.simpleAccess.SimpleAccessOperations;

/**
 * A concrete subclass of <code>RepositoryProvider</code> is created for each
 * project that is shared via a repository. 
 * 
 * @see RepositoryProviderType
 *
 * @since 2.0
 */
public abstract class RepositoryProvider implements IProjectNature {
	
	/**
	 * Default constructor required for the resources plugin to instantiate this class from a
	 * extension definition.
	 */
	public RepositoryProvider() {
	}
	
	/**
	 * Configures the nature for the given project. This method is called after <code>setProject</code>
	 * and before the nature is added to the project. If an exception is generated during configuration
	 * of the project the nature will not be assigned to the project.
	 * 
	 * @throws CoreException if the configuration fails. 
	 */
	abstract public void configureProject() throws CoreException;
	
	/**
	 * Configures the nature for the given project. This method is called after <code>setProject</code> 
	 * and before the nature is added to the project.
	 * <p>
	 * The default behavior for <code>RepositoryProvider</code> subclasses is to fail the configuration
	 * if a provider is already associated with the given project. Subclasses cannot override this method
	 * but must instead override <code>configureProject</code>.
	 * 
	 * @throws CoreException if this method fails. If the configuration fails the nature will not be added 
	 * to the project.
	 * @see IProjectNature#configure
	 */
	final public void configure() throws CoreException {
		RepositoryProvider provider = RepositoryProviderType.getProvider(getProject());
		try {
			configureProject();
		} catch(CoreException e) {
			try {
				TeamPlugin.removeNatureFromProject(getProject(), getID(), null);
			} catch(TeamException e2) {
				throw new CoreException(new Status(IStatus.ERROR, TeamPlugin.ID, 0, Policy.bind("RepositoryProvider_Error_removing_nature_from_project___1") + provider, e2)); //$NON-NLS-1$
			}
			throw e;
		}
	}

	abstract public String getID();

	/*
	 * Provisional.
 	 * Returns an object which implements a set of provider neutral operations for this 
 	 * provider. Answers <code>null</code> if the provider does not wish to support these 
 	 * operations.
 	 * 
 	 * @return the repository operations or <code>null</code> if the provider does not
 	 * support provider neutral operations.
 	 */
	public SimpleAccessOperations getSimpleAccess() {
 		return null;
 	}

	/**
	 * Returns an <code>IFileModificationValidator</code> for pre-checking operations 
 	 * that modify the contents of files.
 	 * Returns <code>null</code> if the provider does not wish to participate in
 	 * file modification validation.
 	 * 
	 * @see org.eclipse.core.resources.IFileModificationValidator
	 */
	
	public IFileModificationValidator getFileModificationValidator() {
		return null;
	}
	
	/**
	 * Returns an <code>IMoveDeleteHook</code> for handling moves and deletes
	 * that occur withing projects managed by the provider. This allows providers 
	 * to control how moves and deletes occur and includes the ability to prevent them. 
	 * <p>
	 * Returning <code>null</code> signals that the default move and delete behavior is desired.
	 * 
	 * @see org.eclipse.core.resources.IMoveDeleteHook
	 */
	public IMoveDeleteHook getMoveDeleteHook() {
		return null;
	}
	
	/**
	 * Returns a brief description of this provider. The exact details of the
	 * representation are unspecified and subject to change, but the following
	 * may be regarded as typical:
	 * 
	 * "SampleProject:org.eclipse.team.cvs.provider"
	 * 
	 * @return a string description of this provider
	 */
	public String toString() {
		return getProject().getName() + ":" + getID(); //$NON-NLS-1$
	}
}
