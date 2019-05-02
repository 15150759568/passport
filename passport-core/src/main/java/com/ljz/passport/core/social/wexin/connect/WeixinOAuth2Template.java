package com.ljz.passport.core.social.wexin.connect;

import com.alibaba.fastjson.JSONArray;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.social.oauth2.AccessGrant;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.social.oauth2.OAuth2Template;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author 李建珍
 * @date 2019/5/1
 */
public class WeixinOAuth2Template extends OAuth2Template {

    private String clientId;

    private String clientSecret;

    private String accessTokenUrl;

    private static final String REFRESH_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/refresh_token";

    /**
     * 日志工具类
     */
    private Logger logger = LoggerFactory.getLogger(getClass());

    public WeixinOAuth2Template(String clientId, String clientSecret, String authorizeUrl, String accessTokenUrl) {
        super(clientId, clientSecret, authorizeUrl, accessTokenUrl);
        setUseParametersForClientAuthentication(true);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.accessTokenUrl = accessTokenUrl;
    }


    /**
     * 调整为微信需要的参数
     *
     * @param authorizationCode
     * @param redirectUri
     * @param additionalParameters
     * @return
     */
    @Override
    public AccessGrant exchangeForAccess(String authorizationCode, String redirectUri,
                                         MultiValueMap<String, String> additionalParameters) {
        StringBuilder accessTokenRequsetUrl = new StringBuilder(accessTokenUrl);

        //clientId
        accessTokenRequsetUrl.append("?appid=" + clientId);
        //clientSecret
        accessTokenRequsetUrl.append("&secret=" + clientSecret);
        accessTokenRequsetUrl.append("&code=" + authorizationCode);
        accessTokenRequsetUrl.append("&grant_type=authorization_code");
        accessTokenRequsetUrl.append("&redirect_uri=" + redirectUri);

        return getAccessToken(accessTokenRequsetUrl);
    }

    /**
     * 刷新或续期access_token使用
     *
     * @param refreshToken
     * @param additionalParameters
     * @return
     */
    @Override
    public AccessGrant refreshAccess(String refreshToken, MultiValueMap<String, String> additionalParameters) {
        StringBuilder refreshTokenUrl = new StringBuilder(REFRESH_TOKEN_URL);
        refreshTokenUrl.append("?appid=" + clientId);
        refreshTokenUrl.append("&grant_type=refresh_token");
        refreshTokenUrl.append("&refresh_token=" + refreshToken);
        return getAccessToken(refreshTokenUrl);
    }

    /**
     * 获取访问令牌,返回访问权限,微信定义的正确的返回
     *
     * @param accessTokenRequestUrl
     * @return
     */
    @SuppressWarnings("unchecked")
    private AccessGrant getAccessToken(StringBuilder accessTokenRequestUrl) {
        logger.info("获取access_token, 请求URL: " + accessTokenRequestUrl.toString());
        String response = getRestTemplate().getForObject(accessTokenRequestUrl.toString(), String.class);
        logger.info("获取access_token, 响应内容: " + response);
        Map<String, Object> result = null;
        try {
            //将返回的json串转换成map
            result = JSONArray.parseObject(response, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //返回错误码时直接返回空
        if (StringUtils.isNotBlank(MapUtils.getString(result, "errcode"))) {
            String errcode = MapUtils.getString(result, "errcode");
            String errmsg = MapUtils.getString(result, "errmsg");
            throw new RuntimeException("获取access token失败, errcode:" + errcode + ", errmsg:" + errmsg);
        }
        WeixinAccessGrant accessToken = new WeixinAccessGrant(
                MapUtils.getString(result, "access_token"),
                MapUtils.getString(result, "scope"),
                MapUtils.getString(result, "refresh_token"),
                MapUtils.getLong(result, "expires_in"));

        accessToken.setOpenId(MapUtils.getString(result, "openid"));
        return accessToken;
    }


    @Override
    public String buildAuthenticateUrl(OAuth2Parameters parameters) {
        String url = super.buildAuthenticateUrl(parameters);
        url += "&appid=" + clientId + "&scop=snsapi_login";
        return url;
    }

    @Override
    protected RestTemplate getRestTemplate() {
        RestTemplate restTemplate = super.getRestTemplate();
        restTemplate.getMessageConverters().add(new StringHttpMessageConverter(Charset.forName("UTF-8")));
        return restTemplate;
    }
}