/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketlink.identity.federation.api.wstrust;

import java.security.Principal;

import org.picketlink.identity.federation.core.exceptions.ParsingException;
import org.picketlink.identity.federation.core.wstrust.STSClient;
import org.picketlink.identity.federation.core.wstrust.STSClientConfig;
import org.picketlink.identity.federation.core.wstrust.STSClientFactory;
import org.picketlink.identity.federation.core.wstrust.WSTrustException;
import org.picketlink.identity.federation.core.wstrust.STSClientConfig.Builder;
import org.w3c.dom.Element;

/**
 * WS-Trust Client
 * 
 * @author Anil.Saldhana@redhat.com
 * @since Aug 29, 2009
 */
public class WSTrustClient
{
   /**
    * The array of STSClient instances that this class delegates to.
    */
   private STSClient[] clients;

   public static class SecurityInfo
   {
      private String username;

      private String passwd;

      public SecurityInfo(String name, char[] pass)
      {
         username = name;
         passwd = new String(pass);
      }

      public SecurityInfo(String name, String pass)
      {
         username = name;
         passwd = pass;
      }
   }

   public WSTrustClient(String serviceName, String port, String endpointURI, SecurityInfo secInfo)
         throws ParsingException
   {
      this(serviceName, port, new String[]
      {endpointURI}, secInfo);
   }

   public WSTrustClient(String serviceName, String port, String[] endpointURIs, SecurityInfo secInfo)
         throws ParsingException
   {
      // basic input validation.
      if (serviceName == null || port == null || endpointURIs == null || secInfo == null)
         throw new IllegalArgumentException(
               "The service name, port, endpoint URIs and security info parameters cannot be null");
      if (endpointURIs.length == 0)
         throw new IllegalArgumentException("At least one endpoint URI must be informed");

      // create an STSClient for each endpointURI.
      this.clients = new STSClient[endpointURIs.length];
      Builder builder = new STSClientConfig.Builder();
      builder.serviceName(serviceName).portName(port).username(secInfo.username).password(secInfo.passwd);

      int index = 0;
      for (String endpointURI : endpointURIs)
      {
         builder.endpointAddress(endpointURI);
         this.clients[index++] = STSClientFactory.getInstance().create(builder.build());
      }

   }

   /**
    * This method will send a RequestSecurityToken with a RequestType of issue
    * and the passed-in tokenType identifies the type of token to be issued by
    * the STS.
    * 
    * @param tokenType - The type of token to be issued.
    * @return Element - The Security Token element. Will be of the tokenType specified.
    * @throws WSTrustException
    */
   public Element issueToken(String tokenType) throws WSTrustException
   {
      return this.issueInternal(null, tokenType, null, 0);
   }

   /**
    * This method will send a RequestSecurityToken with a RequestType of issue
    * and the passed-in endpointURI identifies the ultimate recipient of the token.
    * 
    * @param endpointURI - The ultimate recipient of the token. This will be set at the AppliesTo for
    *                      the RequestSecurityToken which is an optional element so it may be null.
    * @return Element - The Security Token element. Will be of the tokenType configured for the endpointURI.
    * @throws WSTrustException
    */
   public Element issueTokenForEndpoint(String endpointURI) throws WSTrustException
   {
      return this.issueInternal(endpointURI, null, null, 0);
   }

   /**
    * Issues a Security Token from the STS. This methods has the option of 
    * specifying both or one of endpointURI/tokenType but at least one must 
    * specified.
    * 
    * @param endpointURI - The ultimate recipient of the token. This will be set at the AppliesTo for
    *                      the RequestSecurityToken which is an optional element so it may be null.
    * @param tokenType - The type of security token to be issued.
    * @return Element - The Security Token Element issued.
    * @throws IllegalArgumentException If neither endpointURI nor tokenType was specified.
    * @throws WSTrustException
    */
   public Element issueToken(String endpointURI, String tokenType) throws WSTrustException
   {
      return this.issueInternal(endpointURI, tokenType, null, 0);
   }

   public Element issueTokenOnBehalfOf(String endpointURI, String tokenType, Principal principal)
         throws WSTrustException
   {
      return this.issueInternal(endpointURI, tokenType, principal, 0);
   }

   /**
    * This method will send a RequestSecurityToken with a RequestType of renew
    * and the passed-in tokenType identifies the type of token to be renewed by 
    * the STS.
    * 
    * @param tokenType - The type of token to be renewed.
    * @param token - The security token to be renewed.
    * @return Element - The Security Token element. Will be of the tokenType specified.
    */
   public Element renewToken(String tokenType, Element token) throws WSTrustException
   {
      return this.renewInternal(tokenType, token, 0);
   }

   /**
    * This method will send a RequestSecurityToken with a RequestType of validated by
    * the STS.
    * 
    * @param token - The security token to be validated.
    * @return true - If the security token was sucessfully valiated.
    */
   public boolean validateToken(Element token) throws WSTrustException
   {
      return this.validateInternal(token, 0);
   }

   /**
    * <p>
    * This method sends a WS-Trust cancel message to the STS in order to cancel (revoke) the specified security token.
    * </p>
    * 
    * @param token the security token to be canceled.
    * @return {@code true} if the token was successfully canceled; {@code false} otherwise.
    * @throws WSTrustException if an error occurs while canceling the security token.
    */
   public boolean cancelToken(Element token) throws WSTrustException
   {
      return this.cancelInternal(token, 0);
   }

   /**
    * <p>
    * This method issues a token of the specified type for the specified service endpoint and has failover support when
    * more than one endpoint URI has been provided in the constructor. If a {@code ConnectException} occurs when sending
    * the WS-Trust request to one endpoint, the code makes a new attempt using the next URI until the request reaches an
    * STS instance or all URIs have been tried.
    * </p>
    * 
    * @param serviceEndpointURI a {@code String} representing the endpoint URI of the service that will be the ultimate
    * recipient of the security token.  
    * @param tokenType a {@code String} representing the type of token to be issued.
    * @param principal the {@code Principal} on behalf of whom the token will be issued.
    * @param clientIndex an {@code int} that indicates which of the {@code STSClient} instances should be used to perform
    * the request.
    * @return an {@code Element} representing the security token that has been issued.
    * @throws WSTrustException if a WS-Trust exception is thrown by the STS.
    */
   private Element issueInternal(String serviceEndpointURI, String tokenType, Principal principal, int clientIndex)
         throws WSTrustException
   {
      STSClient client = this.clients[clientIndex];
      try
      {
         return client.issueTokenOnBehalfOf(serviceEndpointURI, tokenType, principal);
      }
      catch (RuntimeException e)
      {
         // if this was a connection refused exception and we still have clients to try, call the next client.
         if (this.isCausedByConnectException(e) && clientIndex < this.clients.length - 1)
         {
            return this.issueInternal(serviceEndpointURI, tokenType, principal, ++clientIndex);
         }
         throw e;
      }
   }

   /**
    * <p>
    * This method renews the specified token and has failover support when more than one endpoint URI has been provided
    * in the constructor. If a {@code ConnectException} occurs when sending the WS-Trust request to one endpoint, the
    * code makes a new attempt using the next URI until the request reaches an STS instance or all URIs have been tried.
    * </p>
    * 
    * @param tokenType the type of the token being renewed.
    * @param token an {@code Element} representing the security token being renewed.
    * @param clientIndex an {@code int} that indicates which of the {@code STSClient} instances should be used to perform
    * the request.
    * @return an {@code Element} representing the security token that has been renewed.
    * @throws WSTrustException if a WS-Trust exception is thrown by the STS.
    */
   private Element renewInternal(String tokenType, Element token, int clientIndex) throws WSTrustException
   {
      STSClient client = this.clients[clientIndex];
      try
      {
         return client.renewToken(tokenType, token);
      }
      catch (RuntimeException e)
      {
         // if this was a connection refused exception and we still have clients to try, call the next client.
         if (this.isCausedByConnectException(e) && clientIndex < this.clients.length - 1)
         {
            return this.renewInternal(tokenType, token, ++clientIndex);
         }
         throw e;
      }
   }

   /**
    * <p>
    * This method validates the specified token and has failover support when more than one endpoint URI has been provided
    * in the constructor. If a {@code ConnectException} occurs when sending the WS-Trust request to one endpoint, the
    * code makes a new attempt using the next URI until the request reaches an STS instance or all URIs have been tried.
    * </p>
    * 
    * @param token an {@code Element} representing the security token being validated.
    * @param clientIndex an {@code int} that indicates which of the {@code STSClient} instances should be used to perform
    * the request.
    * @return {@code true} if the token was considered valid; {@code false} otherwise.
    * @throws WSTrustException if a WS-Trust exception is thrown by the STS.
    */
   private boolean validateInternal(Element token, int clientIndex) throws WSTrustException
   {
      STSClient client = this.clients[clientIndex];
      try
      {
         return client.validateToken(token);
      }
      catch (RuntimeException e)
      {
         // if this was a connection refused exception and we still have clients to try, call the next client.
         if (this.isCausedByConnectException(e) && clientIndex < this.clients.length - 1)
         {
            return this.validateInternal(token, ++clientIndex);
         }
         throw e;
      }
   }

   /**
    * <p>
    * This method cancels the specified token and has failover support when more than one endpoint URI has been provided
    * in the constructor. If a {@code ConnectException} occurs when sending the WS-Trust request to one endpoint, the
    * code makes a new attempt using the next URI until the request reaches an STS instance or all URIs have been tried.
    * </p>
    * 
    * @param token an {@code Element} representing the security token being canceled.
    * @param clientIndex an {@code int} that indicates which of the {@code STSClient} instances should be used to perform
    * the request.
    * @return {@code true} if the token was canceled; {@code false} otherwise.
    * @throws WSTrustException if a WS-Trust exception is thrown by the STS.
    */
   private boolean cancelInternal(Element token, int clientIndex) throws WSTrustException
   {
      STSClient client = this.clients[clientIndex];
      try
      {
         return client.cancelToken(token);
      }
      catch (RuntimeException e)
      {
         // if this was a connection refused exception and we still have clients to try, call the next client.
         if (this.isCausedByConnectException(e) && clientIndex < this.clients.length - 1)
         {
            return this.cancelInternal(token, ++clientIndex);
         }
         throw e;
      }
   }

   /**
    * <p>
    * Checks if the root of the specified {@code Throwable} is an instance of {@code java.net.ConnectException}. 
    * </p>
    * 
    * @param throwable the {@code Throwable} that will be inspected.
    * @return {@code true} if the root cause is a {@code java.net.ConnectException}; {@code false} otherwise.
    */
   private boolean isCausedByConnectException(Throwable throwable)
   {
      // iterate through the causes until we reach the root cause.
      while (throwable.getCause() != null)
         throwable = throwable.getCause();

      // check if the root throwable is a ConnectException.
      if (throwable instanceof java.net.ConnectException && throwable.getMessage().equals("Connection refused"))
         return true;
      return false;
   }
}