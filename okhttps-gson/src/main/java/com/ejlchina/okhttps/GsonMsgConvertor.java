package com.ejlchina.okhttps;

import com.ejlchina.data.GsonDataConvertor;
import com.google.gson.Gson;

public class GsonMsgConvertor extends GsonDataConvertor implements MsgConvertor, ConvertProvider {

	public GsonMsgConvertor() { }
	
	public GsonMsgConvertor(Gson gson) {
		super(gson);
	}

	@Override
	public String mediaType() {
		return "application/json";
	}

	@Override
	public MsgConvertor getConvertor() {
		return new GsonMsgConvertor();
	}

}














