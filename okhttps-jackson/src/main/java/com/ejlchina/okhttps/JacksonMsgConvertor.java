package com.ejlchina.okhttps;

import com.ejlchina.data.JacksonDataConvertor;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonMsgConvertor extends JacksonDataConvertor implements MsgConvertor, ConvertProvider {

	public JacksonMsgConvertor() { }
	
	public JacksonMsgConvertor(ObjectMapper objectMapper) {
		super(objectMapper);
	}

	public JacksonMsgConvertor(ObjectMapper objectMapper, boolean typeCached) {
		super(objectMapper, typeCached);
	}

	@Override
	public String mediaType() {
		return "application/json; charset={charset}";
	}

	@Override
	public MsgConvertor getConvertor() {
		return new JacksonMsgConvertor();
	}

}
