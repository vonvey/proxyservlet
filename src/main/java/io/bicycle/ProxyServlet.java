package io.bicycle;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;


public class ProxyServlet extends HttpServlet {

    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_HOST_NAME = "Host";

    private static final File FILE_UPLOAD_TEMP_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));

    // Proxy host params

    /**
     * The (optional) protocol http, or https
     */
    private String proxyProtocol;

    /**
     * The host to which we are proxying requests
     */
    private String proxyHost;
    /**
     * The port on the proxy host to which we are proxying requests. Default value is 80.
     */
    private int proxyPort = 80;
    /**
     * The (optional) path on the proxy host to which we are proxying requests. Default value is "".
     */
    private String proxyPath = "";
    /**
     * The maximum size for uploaded files in bytes. Default value is 5MB.
     */
    private int maxFileUploadSize = 5 * 1024 * 1024;

    public String getServletInfo() {
        return "ProxyServlet";
    }

    /**
     * Initialize the <code>io.bicycle.ProxyServlet</code>
     *
     * @param servletConfig The Servlet configuration passed in by the servlet container
     */
    public void init(final ServletConfig servletConfig) {

        final String protocol = servletConfig.getInitParameter("proxyProtocol");
        if ("https".equalsIgnoreCase(protocol)) {
            this.setProxyProtocol(protocol);
        } else {
            this.setProxyProtocol(protocol);
        }

        // Get the proxy host
        String stringProxyHostNew = servletConfig.getInitParameter("proxyHost");
        if (stringProxyHostNew == null || stringProxyHostNew.length() == 0) {
            throw new IllegalArgumentException("Proxy host not set, please set init-param 'proxyHost' in web.xml");
        }
        this.setProxyHost(stringProxyHostNew);
        // Get the proxy port if specified
        String stringProxyPortNew = servletConfig.getInitParameter("proxyPort");
        if (stringProxyPortNew != null && stringProxyPortNew.length() > 0) {
            this.setProxyPort(Integer.parseInt(stringProxyPortNew));
        }
        // Get the proxy path if specified
        String stringProxyPathNew = servletConfig.getInitParameter("proxyPath");
        if (stringProxyPathNew != null && stringProxyPathNew.length() > 0) {
            this.setProxyPath(stringProxyPathNew);
        }
        // Get the maximum file upload size if specified
        String stringMaxFileUploadSize = servletConfig.getInitParameter("maxFileUploadSize");
        if (stringMaxFileUploadSize != null && stringMaxFileUploadSize.length() > 0) {
            this.setMaxFileUploadSize(Integer.parseInt(stringMaxFileUploadSize));
        }
    }

    /**
     * Performs an HTTP GET request
     *
     * @param httpServletRequest  The {@link HttpServletRequest} object passed
     *                            in by the servlet engine representing the
     *                            client request to be proxied
     * @param httpServletResponse The {@link HttpServletResponse} object by which
     *                            we can send a proxied response to the client
     */
    public void doGet(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        final GetMethod getMethodProxyRequest = new GetMethod(this.createProxyUrl(httpServletRequest));

        setProxyRequestHeaders(httpServletRequest, getMethodProxyRequest);

        this.executeProxyRequest(getMethodProxyRequest, httpServletRequest, httpServletResponse);
    }

    /**
     * Performs an HTTP POST request
     *
     * @param httpServletRequest  The {@link HttpServletRequest} object passed
     *                            in by the servlet engine representing the
     *                            client request to be proxied
     * @param httpServletResponse The {@link HttpServletResponse} object by which
     *                            we can send a proxied response to the client
     */
    public void doPost(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        final PostMethod postMethodProxyRequest = new PostMethod(this.createProxyUrl(httpServletRequest));
        setProxyRequestHeaders(httpServletRequest, postMethodProxyRequest);

        if (ServletFileUpload.isMultipartContent(httpServletRequest)) {
            this.handleMultipartPost(postMethodProxyRequest, httpServletRequest);
        } else {
            this.handleStandardPost(postMethodProxyRequest, httpServletRequest);
        }

        this.executeProxyRequest(postMethodProxyRequest, httpServletRequest, httpServletResponse);
    }

    /**
     * Sets up the given {@link PostMethod} to send the same multipart POST
     * data as was sent in the given {@link HttpServletRequest}
     *
     * @param postMethodProxyRequest The {@link PostMethod} that we are
     *                               configuring to send a multipart POST request
     * @param httpServletRequest     The {@link HttpServletRequest} that contains
     *                               the mutlipart POST data to be sent via the {@link PostMethod}
     */
    @SuppressWarnings("unchecked")
    private void handleMultipartPost(final PostMethod postMethodProxyRequest, final HttpServletRequest httpServletRequest)
            throws ServletException {
        final DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();
        diskFileItemFactory.setSizeThreshold(this.getMaxFileUploadSize());
        diskFileItemFactory.setRepository(FILE_UPLOAD_TEMP_DIRECTORY);

        final ServletFileUpload servletFileUpload = new ServletFileUpload(diskFileItemFactory);
        try {
            final List<FileItem> listFileItems = (List<FileItem>) servletFileUpload.parseRequest(httpServletRequest);
            final List<Part> listParts = new ArrayList<Part>();
            for (FileItem fileItemCurrent : listFileItems) {
                if (fileItemCurrent.isFormField()) {
                    listParts.add(new StringPart(fileItemCurrent.getFieldName(), fileItemCurrent.getString()));
                } else {
                    listParts.add(
                            new FilePart(
                                    fileItemCurrent.getFieldName(),
                                    new ByteArrayPartSource(fileItemCurrent.getName(), fileItemCurrent.get())));
                }
            }
            final MultipartRequestEntity multipartRequestEntity =
                    new MultipartRequestEntity(listParts.toArray(new Part[]{}), postMethodProxyRequest.getParams());
            postMethodProxyRequest.setRequestEntity(multipartRequestEntity);
            // The current content-type header (received from the client) IS of
            // type "multipart/form-data", but the content-type header also
            // contains the chunk boundary string of the chunks. Currently, this
            // header is using the boundary of the client request, since we
            // blindly copied all headers from the client request to the proxy
            // request. However, we are creating a new request with a new chunk
            // boundary string, so it is necessary that we re-set the
            // content-type string to reflect the new chunk boundary string
            postMethodProxyRequest.setRequestHeader(HEADER_CONTENT_TYPE, multipartRequestEntity.getContentType());
        } catch (FileUploadException fileUploadException) {
            throw new ServletException(fileUploadException);
        }
    }

    /**
     * Sets up the given {@link PostMethod} to send the same standard POST
     * data as was sent in the given {@link HttpServletRequest}
     *
     * @param postMethodProxyRequest The {@link PostMethod} that we are
     *                               configuring to send a standard POST request
     * @param httpServletRequest     The {@link HttpServletRequest} that contains
     *                               the POST data to be sent via the {@link PostMethod}
     */
    @SuppressWarnings("unchecked")
    private void handleStandardPost(PostMethod postMethodProxyRequest, HttpServletRequest httpServletRequest) {
        final Map<String, String[]> postParameters = (Map<String, String[]>) httpServletRequest.getParameterMap();
        final List<NameValuePair> listNameValuePairs = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String[]> parameterKeyAndValues : postParameters.entrySet()) {
            for (String parameterValue : parameterKeyAndValues.getValue()) {
                listNameValuePairs.add(new NameValuePair(parameterKeyAndValues.getKey(), parameterValue));
            }
        }
        postMethodProxyRequest.setRequestBody(listNameValuePairs.toArray(new NameValuePair[]{}));
    }

    /**
     * Executes the {@link HttpMethod} passed in and sends the proxy response
     * back to the client via the given {@link HttpServletResponse}
     *
     * @param httpMethodProxyRequest An object representing the proxy request to be made
     * @param httpServletResponse    An object by which we can send the proxied
     *                               response back to the client
     * @throws IOException      Can be thrown by the {@link HttpClient}.executeMethod
     * @throws ServletException Can be thrown to indicate that another error has occurred
     */
    private void executeProxyRequest(
            final HttpMethod httpMethodProxyRequest,
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse)
            throws IOException, ServletException
    {
        final HttpClient httpClient = new HttpClient();
        httpMethodProxyRequest.setFollowRedirects(false);

        final int proxyResponseCode = httpClient.executeMethod(httpMethodProxyRequest);

        // Check if the proxy response is a redirect
        // The following code is adapted from org.tigris.noodle.filters.CheckForRedirect
        // Hooray for open source software
        if (proxyResponseCode >= HttpServletResponse.SC_MULTIPLE_CHOICES /* 300 */
                && proxyResponseCode < HttpServletResponse.SC_NOT_MODIFIED /* 304 */) {
            final String location = httpMethodProxyRequest.getResponseHeader(HEADER_LOCATION).getValue();
            if (location == null) {
                throw new ServletException("Recieved status code: " + proxyResponseCode
                        + " but no " + HEADER_LOCATION + " header was found in the response");
            }
            // Modify the redirect to go to this proxy servlet rather that the proxied host
            String myHostName = httpServletRequest.getServerName();
            if (httpServletRequest.getServerPort() != 80) {
                myHostName += ":" + httpServletRequest.getServerPort();
            }
            myHostName += httpServletRequest.getContextPath();
            httpServletResponse.sendRedirect(location.replace(getProxyHostAndPort() + this.getProxyPath(), myHostName));
            return;
        } else if (proxyResponseCode == HttpServletResponse.SC_NOT_MODIFIED) {
            // 304 needs special handling.  See:
            // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
            // We get a 304 whenever passed an 'If-Modified-Since'
            // header and the data on disk has not changed; server
            // responds w/ a 304 saying I'm not going to send the
            // body because the file has not changed.
            httpServletResponse.setIntHeader(HEADER_CONTENT_LENGTH, 0);
            httpServletResponse.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        // Pass the response code back to the client
        httpServletResponse.setStatus(proxyResponseCode);

        // Pass response headers back to the client
        for (Header header : httpMethodProxyRequest.getResponseHeaders()) {
            httpServletResponse.setHeader(header.getName(), header.getValue());
        }

        // Send the content to the client
        final BufferedInputStream bufferedInputStream =
                new BufferedInputStream(httpMethodProxyRequest.getResponseBodyAsStream());
        OutputStream outputStreamClientResponse = httpServletResponse.getOutputStream();
        int intNextByte;
        while ((intNextByte = bufferedInputStream.read()) != -1) {
            outputStreamClientResponse.write(intNextByte);
        }
    }

    /**
     * Retrieves all of the headers from the servlet request and sets them on
     * the proxy request
     *
     * @param httpServletRequest     The request object representing the client's
     *                               request to the servlet engine
     * @param httpMethodProxyRequest The request that we are about to send to
     *                               the proxy host
     */
    @SuppressWarnings("unchecked")
    private void setProxyRequestHeaders(final HttpServletRequest httpServletRequest, final HttpMethod httpMethodProxyRequest) {
        final Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String headerName = headerNames.nextElement();
            if (headerName.equalsIgnoreCase(HEADER_CONTENT_LENGTH))
                continue;
            // As per the Java Servlet API 2.5 documentation:
            //		Some headers, such as Accept-Language can be sent by clients
            //		as several headers each with a different value rather than
            //		sending the header as a comma separated list.
            // Thus, we get an Enumeration of the header values sent by the client
            final Enumeration enumerationOfHeaderValues = httpServletRequest.getHeaders(headerName);
            while (enumerationOfHeaderValues.hasMoreElements()) {
                String stringHeaderValue = (String) enumerationOfHeaderValues.nextElement();
                // In case the proxy host is running multiple virtual servers,
                // rewrite the Host header to ensure that we get content from
                // the correct virtual server
                if (headerName.equalsIgnoreCase(HEADER_HOST_NAME)) {
                    stringHeaderValue = getProxyHostAndPort();
                }
                Header header = new Header(headerName, stringHeaderValue);
                // Set the same header on the proxy request
                httpMethodProxyRequest.setRequestHeader(header);
            }
        }
    }

    private String createProxyUrl(final HttpServletRequest httpServletRequest) {

        String proxyUrl = this.getProxyProtocol() + "://" + this.getProxyHostAndPort();
        // Check if we are proxying to a path other that the document root
        if (!this.getProxyPath().equalsIgnoreCase("")) {
            proxyUrl += this.getProxyPath();
        }
        // Handle the path given to the servlet
        proxyUrl += httpServletRequest.getPathInfo();
        // Handle the query string
        if (httpServletRequest.getQueryString() != null) {
            proxyUrl += "?" + httpServletRequest.getQueryString();
        }
        return proxyUrl;
    }

    private String getProxyHostAndPort() {
        if (this.getProxyPort() == 80 || this.getProxyPort() == 443) {
            return this.getProxyHost();
        } else {
            return this.getProxyHost() + ":" + this.getProxyPort();
        }
    }

    public String getProxyProtocol() {
        return proxyProtocol;
    }

    private void setProxyProtocol(String protocol) {
        this.proxyProtocol = protocol;
    }


    private String getProxyHost() {
        return this.proxyHost;
    }

    private void setProxyHost(String stringProxyHostNew) {
        this.proxyHost = stringProxyHostNew;
    }

    private int getProxyPort() {
        return this.proxyPort;
    }

    private void setProxyPort(int intProxyPortNew) {
        this.proxyPort = intProxyPortNew;
    }

    private String getProxyPath() {
        return this.proxyPath;
    }

    private void setProxyPath(String stringProxyPathNew) {
        this.proxyPath = stringProxyPathNew;
    }

    private int getMaxFileUploadSize() {
        return this.maxFileUploadSize;
    }

    private void setMaxFileUploadSize(int intMaxFileUploadSizeNew) {
        this.maxFileUploadSize = intMaxFileUploadSizeNew;
    }
}