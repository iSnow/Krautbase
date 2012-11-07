package net.krautchan.data;

/*
* Copyright (C) 2011 Johannes Jander (johannes@jandermail.de)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.io.Serializable;

import st.ata.util.FPGenerator;


public abstract class KrautObject implements Serializable {
	private static final long serialVersionUID = 2065174519294721888L;
	/** Krautchan's posting-IDs are not unique across boards, boards have no ID
	 * so we build our own unique id for every data type we transport.
	 * 
	 * NOTE: the contract makes NO stipulations about those IDs being sequential
	 * neither do they have anything to do with the KC-IDs
	 **/
	private Long dbId;
	private String uri;
	public DataEventType type;
	public transient long cachedTime=0; 
	
	
	public Long getDbId() {
		return dbId;
	}

	public String getUri() {
		return uri;
	}

	public void setDbId(Long dbId) {
		this.dbId = dbId;
	}

	public void setUri(String uri) {
		this.uri = uri;
		setDbId(FPGenerator.std64.fp(uri));
	}

	public enum DataEventType {
	    ADD,
	    REMOVE,
	    MODIFY
	}
	
}
