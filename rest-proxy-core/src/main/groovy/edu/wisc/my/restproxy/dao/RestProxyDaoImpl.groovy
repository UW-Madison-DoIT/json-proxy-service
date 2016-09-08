package edu.wisc.my.restproxy.dao

import edu.wisc.my.restproxy.JWTUtils
import edu.wisc.my.restproxy.model.ProxyAuthMethod
import org.springframework.http.HttpStatus;

import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import edu.wisc.my.restproxy.model.ProxyRequestContext;

/**
 * {@link RestProxyDao} implementation backed by a {@link RestTemplate}.
 *
 * A default {@link RestTemplate} instance is provided, but consumers are strongly recommended to
 * configure their own instance and inject. However, this class will always use
 * {@link RestProxyResponseErrorHandler} because it's the client's responsiblity to deal with
 * errors.
 *
 * @author Nicholas Blair
 */
@Service
public class RestProxyDaoImpl implements RestProxyDao, InitializingBean {

  @Autowired(required=false)
  private RestTemplate restTemplate = new RestTemplate();

  /* (non-Javadoc)
   * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    this.restTemplate.setErrorHandler(new RestProxyResponseErrorHandler());
  };

  private static final Logger logger = LoggerFactory.getLogger(RestProxyDaoImpl.class);
  /* (non-Javadoc)
   * @see edu.wisc.my.restproxy.dao.RestProxyDao#proxyRequest(edu.wisc.my.restproxy.model.ProxyRequestContext)
   */
  @Override
  public ResponseEntity<Object> proxyRequest(ProxyRequestContext context) {
    HttpHeaders headers = new HttpHeaders();
    if(context.authMethod == ProxyAuthMethod.BASIC
            && StringUtils.isNotBlank(context.getUsername())
            && null != context.getPassword()) {
      StringBuffer credsBuffer = new StringBuffer(context.getUsername());
      credsBuffer.append(":");
      credsBuffer.append(context.getPassword());
      String creds = credsBuffer.toString();
      byte[] base64CredsBytes = Base64.encodeBase64(creds.getBytes());
      String base64Creds = new String(base64CredsBytes);
      headers.add("Authorization", "Basic " + base64Creds);
    }else if (context.authMethod == ProxyAuthMethod.JWT
                && null != context.getJwtDetails().isValid()) {
      //go get token
      String token = getToken(context);
      //add token to header
      headers.add("Authorization", "Bearer ${token}");
    }

    for(Entry<String, String> entry: context.getHeaders().entries()) {
      headers.add(entry.getKey(), entry.getValue());
    }

    HttpEntity<Object> request = context.getRequestBody() == null ? new HttpEntity<Object>(headers) : new HttpEntity<Object>(context.getRequestBody().getBody(), headers);
    ResponseEntity<Object> response = restTemplate.exchange(context.getUri(),
          context.getHttpMethod(), request, Object.class, context.getAttributes());
    logger.trace("completed request for {}, response= {}", context, response);
    return response;
  }

  private String getToken(ProxyRequestContext context) {
    HttpEntity<Object> request = new HttpEntity<Object>();
    Map<String, String> parameters = new HashMap<>();
    parameters.put("grant_type",context.getJwtDetails().getGrantType());
    parameters.put("assertion", JWTUtils.generateToken(context.getJwtDetails()));

    ResponseEntity response = restTemplate.postForEntity(context.getJwtDetails().getTokenURL(), request, Object.class, parameters);
    if(response.statusCode == HttpStatus.OK && response.hasBody()) {
      return response.body.access_token;
    } else {
      //throw exception, world ends, oh nos!!
      logger.error("Issue getting token for " + context.getResourceKey());
      throw new Exception("Issue getting token for " + context.getResourceKey());
    }
  }
}
