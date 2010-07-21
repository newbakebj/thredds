/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package ucar.nc2.util.net;


//import opendap.dap.DConnect2;
//import ucar.unidata.io.http.HTTPRandomAccessFile;

import java.io.*;
import java.util.zip.InflaterInputStream;
import java.util.zip.GZIPInputStream;

import opendap.dap.DAPHeader;
import opendap.dap.DAPMethod;
import opendap.dap.DAPSession;
import opendap.dap.DAPException;
import org.apache.commons.httpclient.HttpHost;
import  org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.params.HttpClientParams;
import ucar.nc2.util.IO;


/**
 * Manage HttpSession Client protocol settings.
 * <pre>
 * Example:
 *   CredentialsProvider provider = new thredds.ui.UrlAuthenticatorDialog(frame);
 * opendap.dap.HttpWrapClientManager.init(provider, "ToolsUI");
 * <p/>
 * </pre>
 *
 * @author caron
 */
public class HttpClientManager {
  static private boolean debug = false;
  static private int timeout = 0;

   DAPSession _client = null;



  /**
   * initialize the AbstractHttpClient layer.
   *
   * @param provider  CredentialsProvider.
   * @param userAgent Content of User-Agent header, may be null
   */
  public DAPSession init(CredentialsProvider provider, String userAgent) throws IOException {
    initHttpClient();

    if (provider != null)
      _client.setCredentialsProvider(provider);

    if (userAgent != null)
      _client.setUserAgent(userAgent + "/NetcdfJava/HttpClient");
    else
        _client.setUserAgent("/NetcdfJava/HttpClient");

    // nick.bower@metoceanengineers.com
    String proxyHost = System.getProperty("http.proxyHost");
    String proxyPort = System.getProperty("http.proxyPort");
    if ((proxyHost != null) && (proxyPort != null) && !proxyPort.trim().equals("")) {
      HttpHost proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort));
      _client.setProxy(proxy);
    }
    return _client;
  }

  /**
   * Get the HttpClient object - a single instance is used.
   *
   * @return the  HttpClient object
   */
  /*
   public HttpSession getHttpClient() {
      return _client;
  }
  */
  private void initHttpClient() throws IOException {

    if (_client != null) return;
    _client = new DAPSession();

    // allow self-signed certificates

    // LOOK need default CredentialsProvider ??
    // _client.getParams().setParameter(CredentialsProvider.PROVIDER, provider);

  }

  public static void clearState() {
    //_client.close();
  }

  /**
   * Get the content from a url. For large returns, its better to use getResponseAsStream.
   *
   * xx@param urlString url as a String
   * @return contents of url as a String
   * @throws java.io.IOException on error
   *
  public String getContent(String urlString) throws IOException {

    try {
      _client.newMethod("get",(urlString);
      return _client.getContentString();
    } finally {
      // _client.close();
    }
  } */

  static void initMethod(DAPMethod method)
  {
       method.setParameter(HttpClientParams.SO_TIMEOUT, (Object)timeout);
       method.setParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS, (Object) Boolean.TRUE);
       method.setParameter(HttpClientParams.COOKIE_POLICY, (Object) CookiePolicy.RFC_2109);
  }

  /**
   * Put content to a url, using HTTP PUT. Handles one level of 302 redirection.
   *
   * @param urlString url as a String
   * @param content   PUT this content at the given url.
   * @return the HTTP status return code
   * @throws java.io.IOException on error
   */
  static public int putContent(String urlString, String content) throws IOException {
    DAPSession client = null;
      DAPMethod method = null;
    try {
      client = new DAPSession();
        method = client.newMethod("get",urlString);
        initMethod(method);
      method.setRequestHeader("Accept-Encoding", "gzip,deflate");

      method.setContentString(content);
      int resultCode = method.execute();

      if (resultCode == 302) {
        String redirectLocation;
        DAPHeader locationHeader = method.getResponseHeader("location");
        if (locationHeader != null) {
          redirectLocation = locationHeader.getValue();
          if (debug) System.out.println("***Follow Redirection = " + redirectLocation);
          resultCode = putContent(redirectLocation, content);
        }
      }
      return resultCode;

    } finally {
        if (method != null) method.close();
      if (client != null) client.close();
    }
  }

  //////////////////////

  static public String getContent(String urlString) {

    DAPSession client = null;
      DAPMethod method = null;
    try {
      client = new DAPSession();
        method = client.newMethod("get",urlString);
      method.setRequestHeader("Accept-Encoding", "gzip,deflate");
      int status = method.execute();
      if (status != 200) {
        throw new DAPException("failed status = " + status);
      }

      String charset = method.getCharSet();
      if (charset == null) charset = "UTF-8";

      // check for deflate and gzip compression
      DAPHeader h = method.getResponseHeader("content-encoding");
      String encoding = (h == null) ? null : h.getValue();

      if (encoding != null && encoding.equals("deflate")) {
        InputStream body = method.getContentStream();
        InputStream is = new BufferedInputStream(new InflaterInputStream(body), 10000);
        return IO.readContents(is, charset); //readContents(is, charset, maxKbytes);

      } else if (encoding != null && encoding.equals("gzip")) {
        InputStream body = method.getContentStream();
        InputStream is = new BufferedInputStream(new GZIPInputStream(body), 10000);
        return IO.readContents(is, charset); //readContents(is, charset, maxKbytes);

      } else {
        return method.getContentString(charset);
      }

    } catch (Exception e) {
      e.printStackTrace();
      return null;

    } finally {
      if (method != null) method.close();
        if (client != null) client.close();

    }
  }

  static public void copyUrlContentsToFile(String urlString, File file) {

    DAPSession client = null;
      DAPMethod method = null;
    try {
      client = new DAPSession();
        method = client.newMethod("get",urlString);

      method.setRequestHeader("Accept-Encoding", "gzip,deflate");

      int status = method.execute();

      if (status != 200) {
        throw new RuntimeException("failed status = " + status);
      }

      String charset = method.getCharSet();
      if (charset == null) charset = "UTF-8";

      // check for deflate and gzip compression
      DAPHeader h = method.getResponseHeader("content-encoding");
      String encoding = (h == null) ? null : h.getValue();

      if (encoding != null && encoding.equals("deflate")) {
        InputStream is = new BufferedInputStream(new InflaterInputStream(method.getContentStream()), 10000);
        IO.writeToFile(is, file.getPath());

      } else if (encoding != null && encoding.equals("gzip")) {
        InputStream is = new BufferedInputStream(new GZIPInputStream(method.getContentStream()), 10000);
        IO.writeToFile(is, file.getPath());

      } else {
        IO.writeToFile(method.getContentStream(), file.getPath());
      }

    } catch (Exception e) {
      e.printStackTrace();

    } finally {
      if (method != null) method.close();
        if (client != null) client.close();

    }
  }

  static public long appendUrlContentsToFile(String urlString, File file, long start, long end) {
    long nbytes = 0;

    DAPSession client = null;
      DAPMethod method = null;
    try {
      client = new DAPSession();
        method = client.newMethod("get",urlString);

      method.setRequestHeader("Accept-Encoding", "gzip,deflate");
      method.setRequestHeader("Range", "bytes=" + start + "-" + end);
      int status = method.execute();
      if ((status != 200) && (status != 206)) {
        throw new DAPException("failed status = " + status);
      }

      String charset = method.getCharSet();
      if (charset == null) charset = "UTF-8";

      // check for deflate and gzip compression
      DAPHeader h = method.getResponseHeader("content-encoding");
      String encoding = (h == null) ? null : h.getValue();

      if (encoding != null && encoding.equals("deflate")) {
        InputStream is = new BufferedInputStream(new InflaterInputStream(method.getContentStream()), 10000);
        nbytes = IO.appendToFile(is, file.getPath());

      } else if (encoding != null && encoding.equals("gzip")) {
        InputStream is = new BufferedInputStream(new GZIPInputStream(method.getContentStream()), 10000);
        nbytes = IO.appendToFile(is, file.getPath());

      } else {
        nbytes = IO.appendToFile(method.getContentStream(), file.getPath());
      }

    } catch (Exception e) {
      e.printStackTrace();

    } finally {
      if (method != null) method.close();
        if (client != null) client.close();

    }

    return nbytes;
  }

  /* public void showHttpRequestInfo(Formatter f, AbstractHttpMessage m) {
    f.format("HttpClient request %s %s %n", _client.getName(), _client.getURI());
    // fix f.format("   do Authentication=%s%n", _client.getDoAuthentication());
    f.format("   follow Redirects =%s%n", _client.getFollowRedirects());
    // fix f.format("   effectiveVersion =%s%n", m.getEffectiveVersion());
    // fix f.format("   hostAuthState    =%s%n", m.getHostAuthState());

    HttpParams p = m.getParams();
    f.format("   cookie policy    =%s%n", _client.getCookiePolicy());
    f.format("   http version     =%s%n", _client.getProtocolVersion());
    f.format("   timeout (msecs)  =%d%n", _client.getSoTimeout());
    f.format("   virtual host     =%s%n", _client.getVirtualHost());

    f.format("Request Headers = %n");
    Header[] heads = _client.getHeaders();
    for (int i = 0; i < heads.length; i++)
      f.format("  %s", heads[i]);

    f.format("%n");
  }

  static public void showHttpResponseInfo(Formatter f, HttpSession h) {
    f.format("HttpClient response status = %s%n", h.getStatusLine());
    f.format("Reponse Headers = %n");
    Header[] heads = h.getHeaders();
    for (int i = 0; i < heads.length; i++)
      f.format("  %s", heads[i]);
    f.format("%n");
  } */


}
