package org.ametro.catalog.storage.tasks;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.ametro.Constants;
import org.ametro.catalog.Catalog;
import org.ametro.catalog.CatalogMap;
import org.ametro.catalog.storage.CatalogDeserializer;
import org.ametro.catalog.storage.CatalogSerializer;
import org.ametro.util.FileUtil;

import android.content.Context;
import android.os.Parcel;
import android.util.Log;

public abstract class LoadBaseCatalogTask extends CatalogStorageTask {

	/* PERSISTENT STATE */
	protected final File mFile;
	protected final boolean mForceRefresh;
	protected final int mCatalogId;
	protected final long mId;
	
	/* TRANSITITION STATE */
	protected Catalog mCatalog;
	
	public abstract boolean isDerpecated();
	public abstract void refresh() throws Exception;
	
	public boolean isAsync() {
		return false;
	}
	
	public long getTaskId() {
		return mId;
	}
	
	public LoadBaseCatalogTask(int catalogId, File file, boolean forceRefresh){
		this.mCatalogId = catalogId;
		this.mFile = file;
		this.mForceRefresh = forceRefresh;
		this.mId = catalogId + (forceRefresh ? 100 : 0);
	}

	protected LoadBaseCatalogTask(Parcel in) {
		mId = in.readLong();
		mCatalogId = in.readInt();
		mForceRefresh = in.readInt()!=0;
		mFile = new File(in.readString());
	}
	
	protected void begin() {
		super.begin();
	}

	protected void failed(Throwable reason) {
		mCatalog = getCorruptedCatalog();
		super.failed(reason);
	}
	
	protected void run(Context context) throws Exception{
		//mCatalog = ApplicationEx.getInstance().getCatalogStorage().getCatalog(getCatalogId());
		if(mCatalog==null || mForceRefresh){
			if(!mForceRefresh && mFile.exists()){
				update(0, 0, "Loading catalog from XML");
				loadFromStorage();
			}
			if(mCatalog==null || mForceRefresh || isDerpecated()){
				refresh();
				if(mCatalog!=null && !mCatalog.isCorrupted()){
					update(0, 0, "Saving catalog to XML");
					saveToStorage();
				}
			}		
		}
	}

	protected Catalog getEmptyCatalog()
	{
		return new Catalog(System.currentTimeMillis(), getBaseUrl(), new ArrayList<CatalogMap>());
	}
	
	protected Catalog getCorruptedCatalog(){
		Catalog catalog = getEmptyCatalog();
		catalog.setCorrupted(true);
		return catalog;
	}
	
	protected String getBaseUrl(){
		return mFile.getAbsolutePath();
	}
	
	protected void loadFromStorage(){
		BufferedInputStream strm = null;
		try{
			strm = new BufferedInputStream(new FileInputStream(mFile));
			mCatalog = CatalogDeserializer.deserializeCatalog(strm);
		}catch(Exception ex){
			FileUtil.delete(mFile);
		}finally{
			if(strm!=null){
				try { strm.close(); }catch(IOException ex){}
			}
		}		
	}
	
	protected void saveToStorage(){
		try{
			BufferedOutputStream strm = null;
			try{
				strm = new BufferedOutputStream(new FileOutputStream(mFile));
				CatalogSerializer.serializeCatalog(mCatalog, strm);
			}catch(Exception ex){
				if(Log.isLoggable(Constants.LOG_TAG_MAIN, Log.ERROR)){
					Log.e(Constants.LOG_TAG_MAIN, "Failed save catalog", ex);
				}
				failed(ex);
			}finally{
				if(strm!=null){
					try { strm.close(); }catch(IOException ex){}
				}
			}
		}catch(Exception ex){
			failed(ex);
		}		
	}
	
	public Catalog getCatalog() {
		return mCatalog;
	}	
	
	public int getCatalogId(){
		return mCatalogId;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeLong(mId);
		out.writeInt(mCatalogId);
		out.writeInt(mForceRefresh ? 1 : 0);
		out.writeString(mFile.getAbsolutePath());
	}

}
