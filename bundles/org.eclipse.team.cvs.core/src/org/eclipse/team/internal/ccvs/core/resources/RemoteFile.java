package org.eclipse.team.internal.ccvs.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.team.ccvs.core.CVSStatus;
import org.eclipse.team.ccvs.core.CVSTag;
import org.eclipse.team.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.ccvs.core.ICVSRemoteResource;
import org.eclipse.team.ccvs.core.ICVSRepositoryLocation;
import org.eclipse.team.ccvs.core.ILogEntry;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.sync.IRemoteResource;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.Policy;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.core.client.Session;
import org.eclipse.team.internal.ccvs.core.client.Update;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.LogListener;
import org.eclipse.team.internal.ccvs.core.connection.CVSServerException;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.FileUtil;

/**
 * This class provides the implementation of ICVSRemoteFile and IManagedFile for
 * use by the repository and sync view.
 */
public class RemoteFile extends RemoteResource implements ICVSRemoteFile, ICVSFile  {

	// cache for file contents received from the server
	private byte[] contents;
			
	/**
	 * Static method which creates a file as a single child of its parent.
	 * This should only be used when one is only interested in the file alone.
	 * 
	 * The returned RemoteFile represents the base of the local resource.
	 * If the local resource does not have a base, then null is returned
	 * even if the resource does exists remotely (e.g. created by another party).
	 */
	public static RemoteFile getBase(RemoteFolder parent, ICVSFile managed) throws CVSException {
		ResourceSyncInfo info = managed.getSyncInfo();
		if ((info == null) || info.isAdded()) {
			// Either the file is unmanaged or has just been added (i.e. doesn't necessarily have a remote)
			return null;
		}
		RemoteFile file = new RemoteFile(parent, managed.getSyncInfo());
		parent.setChildren(new ICVSRemoteResource[] {file});
		return file;
	}
	
	/**
	 * Static method which creates a file as a single child of its parent.
	 * This should only be used when one is only interested in the file alone.
	 * 
	 * The returned RemoteFile represents the latest remote revision corresponding to the local resource.
	 * If the local resource does not have a base, then null is returned
	 * even if the resource does exists remotely (e.g. created by another party).
	 */
	public static RemoteFile getLatest(RemoteFolder parent, ICVSFile managed, CVSTag tag, IProgressMonitor monitor) throws CVSException {
		ResourceSyncInfo info = managed.getSyncInfo();
		if ((info == null) || info.isAdded()) {
			// Either the file is unmanaged or has just been added (i.e. doesn't necessarily have a remote)
			return null;
		}
		
		RemoteFile file = new RemoteFile(parent, managed.getSyncInfo());
		
		// use the contents of the file on disk so that the server can calculate the relative
		// sync state. This is a trick to allow the server to calculate sync state for us.
		InputStream is = managed.getInputStream();
		OutputStream os = file.getOutputStream();
		try {		
			FileUtil.transfer(is, os);
		} catch(IOException e) {			
		} finally {
			try {
				os.close();
				is.close();
			} catch(IOException e) {
			}
		}
			
		parent.setChildren(new ICVSRemoteResource[] {file});
		if( ! file.updateRevision(tag, monitor)) {
			// If updateRevision returns false then the resource no longer exists remotely
			return null;
		}
		
		// forget local contents. Remote contents will be fetched the next time
		// the returned handle is used.
		file.clearContents();
		return file;
	}
	
	/**
	 * Forget the contents associated with this remote handle.
	 */
	public void clearContents() {
		contents = null;
	}
		
	/**
	 * Constructor for RemoteFile that should be used when nothing is know about the
	 * file ahead of time.
	 */
	// XXX do we need the first two constructors?
	public RemoteFile(RemoteFolder parent, int workspaceSyncState, String name, CVSTag tag) {
		this(parent, workspaceSyncState, name, ResourceSyncInfo.ADDED_REVISION, tag);
	}
	
	public RemoteFile(RemoteFolder parent, int workspaceSyncState, String name, String revision, CVSTag tag) {
		this(parent, workspaceSyncState, new ResourceSyncInfo(name, revision, ResourceSyncInfo.DUMMY_TIMESTAMP, ResourceSyncInfo.USE_SERVER_MODE, tag, ResourceSyncInfo.DEFAULT_PERMISSIONS));
	}
	
	public RemoteFile(RemoteFolder parent, ResourceSyncInfo info) {
		this(parent, Update.STATE_NONE, info);
	}
	
	public RemoteFile(RemoteFolder parent, int workspaceSyncState, ResourceSyncInfo info) {
		this.parent = parent;
		this.info = info;
		setWorkspaceSyncState(workspaceSyncState);
		Assert.isTrue(!info.isDirectory());
	}

	/**
	 * @see ICVSResource#accept(IManagedVisitor)
	 */
	public void accept(ICVSResourceVisitor visitor) throws CVSException {
		visitor.visitFile(this);
	}

	/**
	 * @see ICVSRemoteFile#getContents()
	 */
	public InputStream getContents(final IProgressMonitor monitor) {
		
		try {
			if (contents == null) {
				IStatus status;
				Session s = new Session(getRepository(), parent, false);
				s.open(monitor);
				try {
					status = Command.UPDATE.execute(s,
					Command.NO_GLOBAL_OPTIONS,
					new LocalOption[] { Update.makeTagOption(new CVSTag(info.getRevision(), CVSTag.VERSION)),
						Update.IGNORE_LOCAL_CHANGES },
					new String[] { getName() },
					null,
					monitor);
				} finally {
					s.close();
				}
				if (status.getCode() == CVSStatus.SERVER_ERROR) {
					throw new CVSServerException(status);
				}
			}
			if (contents == null)
				throw new CVSException(Policy.bind("RemoteFile.noContentsReceived", getRemoteLocation(null))); //$NON-NLS-1$
			return new ByteArrayInputStream(contents);
		} catch(CVSException e) {
			return null;
		}
	}
	
	/**
	 * @see ICVSRemoteFile#getLogEntries()
	 */
	public ILogEntry[] getLogEntries(IProgressMonitor monitor) throws CVSException {
		
		// Perform a "cvs log..." with a custom message handler
		final List entries = new ArrayList();
		IStatus status;
		Session s = new Session(getRepository(), parent, false);
		s.open(monitor);
		try {
			status = Command.LOG.execute(s,
			Command.NO_GLOBAL_OPTIONS,
			Command.NO_LOCAL_OPTIONS,
			new String[] { getName() },
			new LogListener(this, entries),
			monitor);
		} finally {
			s.close();
		}
		if (status.getCode() == CVSStatus.SERVER_ERROR) {
			throw new CVSServerException(status);
		}
		return (ILogEntry[])entries.toArray(new ILogEntry[entries.size()]);
	}
	
	/**
	 * @see ICVSRemoteFile#getRevision()
	 */
	public String getRevision() {
		return info.getRevision();
	}
	
	/*
	 * Get a different revision of the remote file.
	 * 
	 * We must also create a new parent since the child is accessed through the parent from within CVS commands.
	 * Therefore, we need a new parent so that we can fecth the contents of the remote file revision
	 */
	public RemoteFile toRevision(String revision) {
		RemoteFolder newParent = new RemoteFolder(null, parent.getRepository(), new Path(parent.getRepositoryRelativePath()), parent.getTag());
		RemoteFile file = new RemoteFile(newParent, getWorkspaceSyncState(), getName(), revision, CVSTag.DEFAULT);
		newParent.setChildren(new ICVSRemoteResource[] {file});
		return file;
	}
	
		/**
	 * @see IManagedFile#getSize()
	 */
	public long getSize() {
		return contents == null ? 0 : contents.length;
	}

	/**
	 * @see IManagedFile#getFileInfo()
	 */
	public ResourceSyncInfo getSyncInfo() {
		return info;
	}

	public ICVSFolder getParent() {
		return parent;
 	}
 	
	/**
	 * @see ICVSResource#getRelativePath(ICVSFolder)
	 */
	public String getRelativePath(ICVSFolder ancestor) throws CVSException {
		String result = parent.getRelativePath(ancestor);
		if (result.length() == 0)
			return getName();
		else
			return result + Session.SERVER_SEPARATOR + getName();
	}
	
	/**
	 * @see ICVSResource#getRemoteLocation(ICVSFolder)
	 */
	public String getRemoteLocation(ICVSFolder stopSearching) throws CVSException {
		return parent.getRemoteLocation(stopSearching) + Session.SERVER_SEPARATOR + getName();
	}
	
	/**
	 * Get the remote path for the receiver relative to the repository location path
	 */
	public String getRepositoryRelativePath() {
		String parentPath = parent.getRepositoryRelativePath();
		return parentPath + Session.SERVER_SEPARATOR + getName();
	}
	
	/**
	 * Return the server root directory for the repository
	 */
	public ICVSRepositoryLocation getRepository() {
		return parent.getRepository();
	}
	
	/**
	 * @see IManagedFile#setFileInfo(FileProperties)
	 * 
	 * This method will either be invoked from the updated handler 
	 * after the contents have been set or from the checked-in handler
	 * which indicates that the remote file is empty.
	 */
	public void setSyncInfo(ResourceSyncInfo fileInfo) {
		info = fileInfo;
		// If the contents is null, the remote file is empty
		if (contents == null)
			contents = new byte[0];
	}

	/**
	 * Set the revision for this remote file.
	 * 
	 * @param revision to associated with this remote file
	 */
	public void setRevision(String revision) {
		info = new ResourceSyncInfo(info.getName(), revision, info.getTimeStamp(), info.getKeywordMode(), info.getTag(), info.getPermissions());
	}		
	
	/*
	 * @see ICVSFile#getInputStream()
	 */
	public InputStream getInputStream() throws CVSException {
		return new ByteArrayInputStream(contents == null ? new byte[0] : contents);
	}
	
	/*
	 * @see ICVSFile#getOutputStream()
	 */
	public OutputStream getOutputStream() throws CVSException {
		// stores the contents of the file when the stream is closed
		// could perhaps be optimized in some manner to avoid excessive array copying
		return new ByteArrayOutputStream() {
			public void close() throws IOException {
				contents = toByteArray();
				super.close();
			}
		};
 	}
 
	public void setReadOnly() throws CVSException {
 	}

	/**
	 * @see IManagedFile#getTimeStamp()
	 */
	public String getTimeStamp() throws CVSFileNotFoundException {
		return info.getTimeStamp();
	}

	/**
	 * @see IManagedFile#setTimeStamp(String)
	 */
	public void setTimeStamp(String date) throws CVSException {
	}

	/**
	 * @see IManagedFile#isDirty()
	 * 
	 * A remote file is never dirty
	 */
	public boolean isDirty() throws CVSException {
		return false;
	}
	
	/**
	 * @see IManagedFile#isModified()
	 */
	public boolean isModified() throws CVSException {
		// it is safe to always consider a remote file handle as modified. This will cause any
		// CVS command to fetch new contents from the server.
		return true;
	}

	/**
	 * @see IManagedFile#moveTo(IManagedFile)
	 */
	public void moveTo(String mFile) throws CVSException, ClassCastException {		
		// Do nothing
	}
	
	/*
	 * @see IRemoteResource#members(IProgressMonitor)
	 */
	public IRemoteResource[] members(IProgressMonitor progress) throws TeamException {
		return new IRemoteResource[0];
	}

	/*
	 * @see IRemoteResource#isContainer()
	 */
	public boolean isContainer() {
		return false;
	}

	/*
	 * @see ICVSResource#isFolder()
	 */
	public boolean isFolder() {
		return false;
	}
	
	public boolean updateRevision(CVSTag tag, IProgressMonitor monitor) throws CVSException {
		return parent.updateRevision(this, tag, monitor);
	}
	
	public boolean equals(Object target) {
		if (this == target)
			return true;
		if (!(target instanceof RemoteFile))
			return false;
		RemoteFile remote = (RemoteFile) target;
		return super.equals(target) && remote.getRevision().equals(getRevision());
	}
}