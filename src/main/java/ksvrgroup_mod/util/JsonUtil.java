
package com.github.kpnmserver.ksvrgroup_mod.util;

import java.lang.reflect.Type;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class JsonUtil{
	public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	public static final Type MAP_TYPE = new TypeToken<HashMap<String, Object>>(){}.getType();

	private JsonUtil(){}

	public static HashMap<String, Object> parseJsonToMap(String str){
		return GSON.fromJson(str, MAP_TYPE);
	}

	public static HashMap<String, Object> asMap(Object... objs){
		if(objs.length % 2 != 0){
			throw new IllegalArgumentException();
		}
		final int size = objs.length / 2;
		final HashMap<String, Object> map = new HashMap<>(size);
		for(int i = 0;i < size;i++){
			map.put(objs[i * 2].toString(), objs[i * 2 + 1]);
		}
		return map;
	}
}
