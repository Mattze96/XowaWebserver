/*
XOWA: the XOWA Offline Wiki Application
Copyright (C) 2012 gnosygnu@gmail.com

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/*
This file is part of XOWA: the XOWA Offline Wiki Application
Copyright (C) 2013 matthiasjasny@gmail.com

This file is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This file is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package gplx.xowa.servers; import gplx.*; import gplx.xowa.*;
import gplx.ios.*; import gplx.json.*;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;
public class Xosrv_webserver {
	public Xosrv_webserver(Xoa_app app) {this.app = app;}
	public Xoa_app App() {return app;} private Xoa_app app;
	public String Parse_page_to_html(Xoa_app app, String wiki_domain_str, String page_ttl_str) {		
		byte[] wiki_domain = ByteAry_.new_utf8_(wiki_domain_str);
		byte[] page_ttl = ByteAry_.new_utf8_(page_ttl_str);
		Xow_wiki wiki = app.Wiki_mgr().Get_by_key_or_make(wiki_domain);
		Xoa_url page_url = Xoa_url.new_(wiki_domain, page_ttl);
		Xoa_ttl ttl = Xoa_ttl.parse_(wiki, page_ttl);
		Xoa_page page = wiki.GetPageByTtl(page_url, ttl);
		app.Gui_mgr().Main_win().Page_(page); // HACK: init gui_mgr's page for output (which server ordinarily doesn't need)
		byte[] output_html = wiki.Html_mgr().Output_mgr().Gen(page, Xoh_wiki_article.Tid_view_read);
		return String_.new_utf8_(output_html);
	}
	public void Run_xowa_cmd(Xoa_app app, String url_encoded_str) {
		String cmd = app.Url_converter_url().Decode_str(url_encoded_str);
		app.Gfs_mgr().Run_str(cmd);
	}
	public void Run() {
		HttpServer server = new HttpServer(this);
		new Thread(server).start();
		app.Usr_dlg().Note_many("", "", "Webserver started: listening on 8080.");
	}
}
class HttpServer implements Runnable {
	private Xosrv_webserver webserver;
	private int webserver_port = 8080;
	public HttpServer(Xosrv_webserver webserver) {
		this.webserver = webserver;
	}
	@SuppressWarnings("resource")
	public void run() {
		ServerSocket WebSocket = null;
		try {
			WebSocket = new ServerSocket(webserver_port);
		} catch (IOException e) {
			e.printStackTrace();
		}
		while (true) {// Listen for a TCP connection request.
			try {
				Socket connectionSocket = WebSocket.accept(); //Construct object to process HTTP request message
				HttpRequest request = new HttpRequest(connectionSocket, webserver.App());
				Thread thread = new Thread(request); //Create new thread to process	      
				thread.start(); //Start the thread	
			} catch (IOException e) {
				e.printStackTrace();
			}      
	     }
	}
}
class HttpRequest implements Runnable{
	final static String CRLF = "\r\n";
	Socket socket;
	Xoa_app app;
	public HttpRequest(Socket socket, Xoa_app app){
		this.socket = socket;
		this.app = app;
	}
	public void run(){
		try {
			InputStream is = socket.getInputStream();
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String request = br.readLine();
			//System.out.println(request);
			String req = request.substring(4, request.length() - 9).trim();
			String wiki_domain = "home";
			String page_name = "Main_Page";
			
			if(!req.contains("%file%")){
				//req = req.substring(req.indexOf("home/matthias/xowa_dev")-1);
				if(req.equals("/")){
					req+="home/wiki/Main_Page";
				}
				if(req.endsWith("wiki/")) req+="Main_Page";
				if(req.endsWith("wiki")) req+="/Main_Page";
			}
			
			if(req.contains("%xowa-cmd%")){
				System.out.println("Command!");
				String cmd = req.substring(req.indexOf("%xowa-cmd%")+20);
				System.out.println(cmd);
				app.Webserver().Run_xowa_cmd(app, cmd);
				dos.writeBytes("test");
				dos.close();
			}else
			if(req.contains("%file%")){
				String path = req.replace("/%file%/", app.Fsys_mgr().Root_dir().To_http_file_str());
				path = path.substring(path.indexOf(app.Fsys_mgr().Root_dir().To_http_file_str())+5);
				if(path.contains("?")){
					path = path.substring(0, path.indexOf("?"));
					System.out.println("Path has parameter");
				}
				FileInputStream fis = new FileInputStream(path);
				
				String statusLine = "HTTP/1.1 200 OK: "; //Set initial values to null
				String contentTypeLine = "Content-Type: " + contentType(path) + CRLF;;

				dos.writeBytes(statusLine);
				dos.writeBytes(contentTypeLine);
				dos.writeBytes(CRLF);
				
				sendBytes(fis, dos);
				fis.close();
				dos.close();
				br.close();
				socket.close();
			}else{
				String[] req_split = req.split("/");
				System.out.println("Request: " +request);
				if(req_split.length >= 1){
					wiki_domain = req_split[1];
				}
				if(req_split.length >= 3){
					page_name = req_split[3];
					for(int i = 4; i <= req_split.length-1; i++){
						page_name += "/"+req_split[i];
					}
					page_name = app.Url_converter_url().Decode_str(page_name);
				}
				//System.out.println("Wiki_Domain: "+wiki_domain+" Page_Name: "+page_name);
				String statusLine = "HTTP/1.1 200 OK: "; //Set initial values to null
				String contentTypeLine = "Content-Type: text/html; charset=utf-8" + CRLF;;

				dos.writeBytes(statusLine);
				dos.writeBytes(contentTypeLine);
				dos.writeBytes(CRLF);
				
				try{
					String page_html = app.Webserver().Parse_page_to_html(app, wiki_domain, page_name);
					page_html = page_html.replaceAll(app.Fsys_mgr().Root_dir().To_http_file_str(),"%file%/");
					page_html = page_html.replaceAll("xowa-cmd", "%xowa-cmd%/xowa-cmd");
					page_html = page_html.replaceAll("<a href=\"/wiki/","<a href=\"/"+wiki_domain+"/wiki/");
					page_html = page_html.replaceAll("action=\"/wiki/", "action=\"/"+wiki_domain+"/wiki/");
					page_html = page_html.replaceAll("/site","");

					dos.write(page_html.getBytes());
					dos.close();
				}catch(Exception err) {
					dos.writeBytes("Site not found");
					dos.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void sendBytes(FileInputStream fis, DataOutputStream dos) {
		byte[] buffer = new byte[1024];
		int bytes = 0;
		try {
			while((bytes= fis.read(buffer)) != -1){
				dos.write(buffer, 0, bytes);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private String contentType(String fileName) {
		if(fileName.endsWith(".htm") || fileName.endsWith(".html"))
			return "text/html";
		if(fileName.endsWith(".jpg"))
			return "text/jpg";
		if(fileName.endsWith(".gif"))
			return "text/gif";
		return "application/octet-stream";
	}
}
