/*  
 * Copyright 2012, Asamm Software, s. r. o.
 * 
 * This file is part of LocusAPI.
 * 
 * LocusAPI is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *  
 * LocusAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *  
 * You should have received a copy of the Lesser GNU General Public
 * License along with LocusAPI. If not, see 
 * <http://www.gnu.org/licenses/lgpl.html/>.
 */

package locus.api.objects;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;
import locus.api.utils.Logger;

public abstract class Storable {

	private static final String TAG = Storable.class.getSimpleName();
	
	/*
	 * Container for inner data
	 */
	private static class BodyContainer {

		// current item version
		int version;
		// data in item
		byte[] data;
	}
	
	// PUBLIC CONSTRUCTORS

    /**
     * Default empty constructor.
     */
	public Storable() {
		reset();
	}

    /**
     * Constructor based on known/loaded source data.
     * @param data loaded data
     * @throws IOException
     */
	public Storable(byte[] data) throws IOException {
		this(new DataReaderBigEndian(data));
	}

    /**
     * Constructor that creates item directly from input stream.
     * @param dr data reader
     * @throws IOException
     */
	public Storable(DataReaderBigEndian dr) throws IOException {
		this();
		read(dr);
	}
	
	/**
	 * Current object version used for storing
	 * @return get current version of object
	 */
	protected abstract int getVersion();
	
	/**
	 * Reset all values to default once
	 */
	public abstract void reset();
	
	/**************************************************/
	// READ PART
	/**************************************************/
	
	// DataReaderBigEndian section
	
	public static Storable read(Class<? extends Storable> claz, DataReaderBigEndian dr) 
			throws IOException, InstantiationException, IllegalAccessException {
    	// read header
		BodyContainer bc = readHeader(dr);

    	// now initialize object. Data are already loaded, so error will not break data flow
		Storable storable = claz.newInstance();
    	storable.readObject(bc.version, new DataReaderBigEndian(bc.data));
		return storable;
	}

    /**
     * Read content of certain item from byte array.
     * @param data array with data
     * @throws IOException
     */
    public void read(byte[] data) throws IOException {
        DataReaderBigEndian dr = new DataReaderBigEndian(data);
        read(dr);
    }

    /**
     * Read content of certain item from existing stream.
     * @param dr stream to read for
     * @throws IOException
     */
	public void read(DataReaderBigEndian dr) throws IOException {
    	// read header
		BodyContainer bc = readHeader(dr);
    	
		// read body
		readObject(bc.version, new DataReaderBigEndian(bc.data));
	}
	
	private static BodyContainer readHeader(DataReaderBigEndian dr) throws IOException {
		// initialize container
		BodyContainer bc = new BodyContainer();

		// read basic data
    	bc.version = dr.readInt();
    	int size = dr.readInt();
    	
    	// check size to prevent OOE
    	if (size < 0 || size > 10 * 1024 * 1024) {
    		throw new IOException("item size too big, size:" + size + ", max: 10MB");
    	}

    	// read object data
    	bc.data = dr.readBytes(size);

    	// return filled container
    	return bc;
	}
	
	/**
	 * Allows to read object, that is not known.
	 * @param dr instance of data reader
	 * @throws IOException
	 */
	public static void readUnknownObject(DataReaderBigEndian dr) throws IOException {
    	// read header. This also allow to skip body of object
		readHeader(dr);
	}
	
	// DataInputStream section

	public void read(DataInputStream input) throws IOException {
    	// read header
		BodyContainer bc = readHeader(input);
    	
		// read body
		readObject(bc.version, new DataReaderBigEndian(bc.data));
	}
	
	private static BodyContainer readHeader(DataInputStream dis) throws IOException {
		// initialize container
		BodyContainer bc = new BodyContainer();

		// read basic data
    	bc.version = dis.readInt();
    	int size = dis.readInt();
    	
    	// check size to prevent OOE
    	if (size < 0 || size > 10 * 1024 * 1024) {
    		throw new IOException("item size too big, size:" + size + ", max: 10MB");
    	}

    	// read object data
    	bc.data = new byte[size];
    	dis.read(bc.data);

    	// return filled container
    	return bc;
	}
	
	/**
	 * This function is called from {@link #read} function. Do not call it directly until you know,
     * what exactly are you doing.
	 * @param version version of loading content
	 * @param dr data reader with content
	 * @throws IOException
	 */
	protected abstract void readObject(int version, DataReaderBigEndian dr) throws IOException;
	
	/**************************************************/
	// WRITE PART
	/**************************************************/
    
	public void write(DataWriterBigEndian dw) throws IOException {
		// write version
		dw.writeInt(getVersion());
		
		// save position and write empty size
		dw.writeInt(0);
		int startSize = dw.size();
		
		// write object itself
		writeObject(dw);
		
		// return back and write 'totalSize'
		int totalSize = dw.size() - startSize;
		if (totalSize > 0) {
			dw.storePosition();
			dw.moveTo(startSize - 4);
			dw.writeInt(totalSize);
			dw.restorePosition();
		}
	}
	
	/**
	 * This function is called from {@link #write} function. Do not call it directly until you know,
     * what exactly are you doing.
	 * @param dw data writer class
	 * @throws IOException
	 */
	protected abstract void writeObject(DataWriterBigEndian dw) throws IOException;
	
	/**
	 * Create precise copy of current object. 
	 * Method is that object is stored into byte stream and then restored
	 * as a new object.
	 * 
	 * @return exact clone of this object
	 * @throws IOException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public Storable getCopy() throws IOException, InstantiationException, IllegalAccessException {
		byte[] data = getAsBytes();
		return read(this.getClass(), new DataReaderBigEndian(data));
	}

    /**
     * Get whole object serialized into byte array.
     * @return serialized object
     */
	public byte[] getAsBytes() {
		try {
			DataWriterBigEndian dw = new DataWriterBigEndian();
			write(dw);
			return dw.toByteArray();
		} catch (IOException e) {
			Logger.logE(TAG, "getAsBytes()", e);
			return null;
		}
	}
	
    /**************************************************/
    // LIST READING/WRITING
	/**************************************************/
	
	public static List<? extends Storable> readList(Class<? extends Storable> claz, 
			byte[] data) throws IOException {
		return new DataReaderBigEndian(data).readListStorable(claz);
	}
	
	public static List<? extends Storable> readList(Class<? extends Storable> claz,
			DataInputStream dis) throws IOException {
		// prepare container
		List<Storable> objs = new ArrayList<>();
				
		// read size
		int count = dis.readInt();
		if (count == 0) {
			return objs;
		}
		
		// read locations
		for (int i = 0; i < count; i++) {
			try {
				Storable item = claz.newInstance();
				item.read(dis);
				objs.add(item);
			} catch (InstantiationException e) {
				Logger.logE(TAG, "readList(" + claz + ", " + dis + ")", e);
			} catch (IllegalAccessException e) {
				Logger.logE(TAG, "readList(" + claz + ", " + dis + ")", e);
			}
		}
		return objs;
	}
	
	// WRITE LIST PART
	
	public static byte[] getAsBytes(List<? extends Storable> data) {
		try {
			DataWriterBigEndian dw = new DataWriterBigEndian();
			dw.writeListStorable(data);
			return dw.toByteArray();
		} catch (Exception e) {
			Logger.logE(TAG, "getAsBytes(" + data + ")", e);
		}
		return null;
	}

	public static void writeList(List<? extends Storable> objs, DataOutputStream dos)
			throws IOException {
		// get size of list
		int size;
		if (objs == null) {
			size = 0;
		} else {
			size = objs.size();
		}

		// write size of list
		dos.writeInt(size);
		if (size == 0) {
			return;
		}

		// write objects
		for (int i = 0, n = objs.size(); i < n; i++) {
			dos.write(objs.get(i).getAsBytes());
		}
	}
}
