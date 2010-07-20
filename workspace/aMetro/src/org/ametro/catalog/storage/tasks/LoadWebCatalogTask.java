/*
 * http://code.google.com/p/ametro/
 * Transport map viewer for Android platform
 * Copyright (C) 2009-2010 Roman.Golovanov@gmail.com and other
 * respective project committers (see project home page)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.ametro.catalog.storage.tasks;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ametro.Constants;
import org.ametro.catalog.Catalog;
import org.ametro.catalog.storage.CatalogDeserializer;
import org.ametro.util.FileUtil;
import org.ametro.util.IDownloadListener;
import org.ametro.util.WebUtil;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class LoadWebCatalogTask extends LoadBaseCatalogTask implements IDownloadListener {

	protected static final long DEPRECATED_TIMEOUT =  60*60*1000; // 1 hour
	
	protected final URI mURI;

	public boolean isDerpecated() {
		if(mCatalog == null) return true;
		return System.currentTimeMillis() > (mCatalog.getTimestamp() + DEPRECATED_TIMEOUT);
	}

	public void refresh() throws Exception {
		try{
			FileUtil.touchDirectory(Constants.TEMP_CATALOG_PATH);
			WebUtil.downloadFileUnchecked(null, mURI, new File(Constants.TEMP_CATALOG_PATH, "catalog.zip"), this);
		} catch(Exception ex){
			mCatalog = getCorruptedCatalog();
		}
	}

	public LoadWebCatalogTask(int catalogId, File file, URI uri, boolean forceRefresh) {
		super(catalogId, file, forceRefresh);
		mURI = uri;
	}

	protected LoadWebCatalogTask(Parcel in) {
		super(in);
		mURI = URI.create(in.readString());
	}
	
	public int describeContents() {
		return 0;
	}
	
	public void writeToParcel(Parcel out, int flags) {
		super.writeToParcel(out, flags);
		out.writeString(mURI.toString());
	}
	
	public static final Parcelable.Creator<LoadWebCatalogTask> CREATOR = new Parcelable.Creator<LoadWebCatalogTask>() {
		public LoadWebCatalogTask createFromParcel(Parcel in) {
			return new LoadWebCatalogTask(in);
		}

		public LoadWebCatalogTask[] newArray(int size) {
			return new LoadWebCatalogTask[size];
		}
	};

	public void onBegin(Object context, File file) {
		FileUtil.delete(file);
	}

	public void onDone(Object context, File file) throws Exception {
		try{
			File catalogFile;
			ZipInputStream zip = null;
			String fileName = null;
			try{
				zip = new ZipInputStream(new FileInputStream(file));
				ZipEntry zipEntry = zip.getNextEntry();
				if(zipEntry != null) { 
					fileName = zipEntry.getName();
					final File outputFile = new File(Constants.TEMP_CATALOG_PATH, fileName); 
					FileUtil.writeToStream(new BufferedInputStream(zip), new FileOutputStream(outputFile), false);
					zip.closeEntry();
				}
				zip.close();
				zip = null;
			}finally{
				if(zip != null){
					try{ zip.close(); } catch(Exception e){}
				}
			}
			if(fileName==null){
				throw new Exception("Invalid map catalog archive");
			}
			catalogFile = new File(Constants.TEMP_CATALOG_PATH, fileName);
			mCatalog = CatalogDeserializer.deserializeCatalog(new BufferedInputStream(new FileInputStream(catalogFile)));
			// set timestamp to now for timeout detection 
			mCatalog.setTimestamp(System.currentTimeMillis());
		}catch(Exception ex){
			if(Log.isLoggable(Constants.LOG_TAG_MAIN, Log.WARN)){
				Log.w(Constants.LOG_TAG_MAIN,"Failed download catalog " + mCatalogId, ex);
			}
			mCatalog = new Catalog();
			mCatalog.setCorrupted(true);
			mCatalog.setBaseUrl(mURI.toString());
			mCatalog.setTimestamp(System.currentTimeMillis());
		}
	}

	public boolean onUpdate(Object context, long position, long total) throws Exception {
		update(position,total, "");
		cancelCheck(); // can throws CanceledException 
		return true;
	}

	/* We do not use this callbacks */
	public void onFailed(Object context, File file, Throwable reason) {
	}
	
	public void onCanceled(Object context, File file) {
	}
	
}
