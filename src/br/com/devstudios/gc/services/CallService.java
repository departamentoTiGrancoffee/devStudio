package br.com.devstudios.gc.services;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.input.CloseShieldInputStream;

import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.sankhya.jape.vo.DynamicVO;

public class CallService {

	private String uri;
	private String method;
	private String body;
	private static HttpURLConnection connection;

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String fire() throws Exception{
		
		DynamicVO configVO = ConfiguracaoDAO.get();
		
		String token = configVO.asString("TOKEN");
		
		BufferedReader reader;
		String line;

		StringBuffer responseContent = new StringBuffer();

		URL url = new URL(uri);
		connection = (HttpURLConnection) url.openConnection();

		connection.setRequestMethod(method);
		connection.setRequestProperty("Authorization","Bearer "+token);
		connection.setRequestProperty("Accept","application/json");
		connection.setRequestProperty("Content-Type","application/json");
		connection.setConnectTimeout(30000);
		connection.setReadTimeout(30000);
		connection.setDoOutput(true);
		connection.setDoInput(true);
		
		if(this.body != null) {
			
			OutputStream os = connection.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");    
			osw.write(this.body);
			osw.flush();
			osw.close();
			
		}
		
		int status = connection.getResponseCode();
		
		System.out.println("Response Pipeturbo:");
		System.out.println(connection.getResponseMessage());
		System.out.println(status);

		if (status > 299) {
			InputStream in = new CloseShieldInputStream(connection.getErrorStream());
			reader = new BufferedReader(new InputStreamReader(in));

			while ((line = reader.readLine()) != null) {
				responseContent.append(line);
			}
			reader.close();
			throw new Exception(responseContent.toString()); 
		} else {
			InputStream in = new CloseShieldInputStream(connection.getInputStream());
			reader = new BufferedReader(new InputStreamReader(in,"UTF-8"));
			//reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"));
			while ((line = reader.readLine()) != null) {
				responseContent.append(line);
			}
			reader.close();
		}

		return responseContent.toString();
	}
}
