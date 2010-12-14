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
package org.picketlink.identity.federation.core.parsers.saml.metadata;

import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.picketlink.identity.federation.core.exceptions.ParsingException;
import org.picketlink.identity.federation.core.parsers.ParserNamespaceSupport;
import org.picketlink.identity.federation.core.parsers.util.SAMLParserUtil;
import org.picketlink.identity.federation.core.parsers.util.StaxParserUtil;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLConstants;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLURIConstants;
import org.picketlink.identity.federation.core.util.NetworkUtil;
import org.picketlink.identity.federation.newmodel.saml.v2.assertion.AttributeType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.AttributeAuthorityDescriptorType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.EndpointType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.EntityDescriptorType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.EntityDescriptorType.EDTChoiceType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.EntityDescriptorType.EDTDescriptorChoiceType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.IDPSSODescriptorType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.IndexedEndpointType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.KeyDescriptorType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.LocalizedNameType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.LocalizedURIType;
import org.picketlink.identity.federation.newmodel.saml.v2.metadata.OrganizationType;
import org.w3c.dom.Element;

/**
 * Parse the SAML Metadata element "EntityDescriptor"
 * @author Anil.Saldhana@redhat.com
 * @since Dec 14, 2010
 */
public class SAMLEntityDescriptorParser implements ParserNamespaceSupport
{ 
   private String EDT = JBossSAMLConstants.ENTITY_DESCRIPTOR.get();
   
   public Object parse(XMLEventReader xmlEventReader) throws ParsingException
   { 
      StartElement startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
      StaxParserUtil.validate(startElement, EDT );
      EntityDescriptorType entityDescriptorType = new EntityDescriptorType();
      
      Attribute entityID = startElement.getAttributeByName( new QName( "entityID" ));
      String entityIDValue = StaxParserUtil.getAttributeValue(entityID);
      if( entityIDValue != null )
      {
         entityDescriptorType.setEntityID(entityIDValue);
      }
      
      //Get the Child Elements
      while( xmlEventReader.hasNext() )
      {
         XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if( xmlEvent instanceof EndElement )
         {
            StaxParserUtil.validate( (EndElement)xmlEvent , EDT);
            StaxParserUtil.getNextEndElement(xmlEventReader);
            break;
         }
         startElement = (StartElement) xmlEvent; 
         String localPart = startElement.getName().getLocalPart();
         
         if( JBossSAMLConstants.IDP_SSO_DESCRIPTOR.get().equals( localPart ))
         { 
            IDPSSODescriptorType idpSSO = parseIDPSSODescriptor(xmlEventReader);
            
            EDTDescriptorChoiceType edtDescChoice = new EDTDescriptorChoiceType( idpSSO );
            EDTChoiceType edtChoice = EDTChoiceType.oneValue( edtDescChoice );
            entityDescriptorType.addChoiceType(edtChoice);
         }
         else if( JBossSAMLConstants.ATTRIBUTE_AUTHORITY_DESCRIPTOR.get().equals( localPart ))
         {   
            AttributeAuthorityDescriptorType attrAuthority = parseAttributeAuthorityDescriptor( xmlEventReader );
            
            EDTDescriptorChoiceType edtDescChoice = new EDTDescriptorChoiceType( attrAuthority );
            EDTChoiceType edtChoice = EDTChoiceType.oneValue( edtDescChoice );
            entityDescriptorType.addChoiceType(edtChoice);  
         }
         else if( JBossSAMLConstants.ORGANIZATION.get().equals( localPart ))
         {
            OrganizationType organization = parseOrganization(xmlEventReader);
            
            entityDescriptorType.setOrganization(organization); 
         }
         else 
            throw new RuntimeException( "Unknown " + localPart );
      }
      return entityDescriptorType;
   }

   public boolean supports(QName qname)
   {
      String nsURI = qname.getNamespaceURI();
      String localPart = qname.getLocalPart();
      
      return nsURI.equals( JBossSAMLURIConstants.ASSERTION_NSURI.get() ) 
           && localPart.equals( JBossSAMLConstants.ENTITY_DESCRIPTOR.get() ); 
   } 
   
   private IDPSSODescriptorType parseIDPSSODescriptor( XMLEventReader xmlEventReader ) throws ParsingException
   {
      StartElement startElement = StaxParserUtil.getNextStartElement( xmlEventReader );
      StaxParserUtil.validate(startElement, JBossSAMLConstants.IDP_SSO_DESCRIPTOR.get() );
      List<String> protocolEnum = SAMLParserUtil.parseProtocolEnumeration(startElement);
      IDPSSODescriptorType idpSSODescriptor = new IDPSSODescriptorType( protocolEnum );
      
      while( xmlEventReader.hasNext() )
      {
         XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if( xmlEvent instanceof EndElement )
         {
            EndElement end = StaxParserUtil.getNextEndElement(xmlEventReader); 
            StaxParserUtil.validate( end , JBossSAMLConstants.IDP_SSO_DESCRIPTOR.get() ); 
            break;
         }
         
         startElement = (StartElement) xmlEvent; 
         String localPart = startElement.getName().getLocalPart();
         
         if( JBossSAMLConstants.ARTIFACT_RESOLUTION_SERVICE.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute bindingAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.BINDING.get() ) );
            String binding = StaxParserUtil.getAttributeValue(bindingAttr);
            
            Attribute locationAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.LOCATION.get() ) );
            String location = StaxParserUtil.getAttributeValue( locationAttr );
            
            IndexedEndpointType endpoint = new IndexedEndpointType( NetworkUtil.createURI( binding ), 
                  NetworkUtil.createURI( location ));
            Attribute isDefault = startElement.getAttributeByName( new QName( JBossSAMLConstants.ISDEFAULT.get() ));
            if( isDefault != null )
            {
               endpoint.setIsDefault( Boolean.parseBoolean( StaxParserUtil.getAttributeValue( isDefault )));
            }
            Attribute index = startElement.getAttributeByName( new QName( JBossSAMLConstants.INDEX.get() ));
            if( index != null )
            {
               endpoint.setIndex( Integer.parseInt( StaxParserUtil.getAttributeValue( index )));
            }
            
            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate( endElement, JBossSAMLConstants.ARTIFACT_RESOLUTION_SERVICE.get() );
            
            idpSSODescriptor.addArtifactResolutionService(endpoint);
         }
         else if( JBossSAMLConstants.SINGLE_LOGOUT_SERVICE.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute bindingAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.BINDING.get() ) );
            String binding = StaxParserUtil.getAttributeValue(bindingAttr);
            
            Attribute locationAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.LOCATION.get() ) );
            String location = StaxParserUtil.getAttributeValue( locationAttr );
            
            EndpointType endpoint = new IndexedEndpointType( NetworkUtil.createURI( binding ), 
                  NetworkUtil.createURI( location ));
            Attribute responseLocation = startElement.getAttributeByName( new QName( JBossSAMLConstants.RESPONSE_LOCATION.get() ));
            if( responseLocation != null )
            {
               endpoint.setResponseLocation( NetworkUtil.createURI( StaxParserUtil.getAttributeValue( responseLocation )));
            } 
            
            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate( endElement, JBossSAMLConstants.SINGLE_LOGOUT_SERVICE.get() );
            
            idpSSODescriptor.addSingleLogoutService( endpoint );
         }
         else if( JBossSAMLConstants.SINGLE_SIGNON_SERVICE.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute bindingAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.BINDING.get() ) );
            String binding = StaxParserUtil.getAttributeValue(bindingAttr);
            
            Attribute locationAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.LOCATION.get() ) );
            String location = StaxParserUtil.getAttributeValue( locationAttr );
            
            EndpointType endpoint = new IndexedEndpointType( NetworkUtil.createURI( binding ), 
                  NetworkUtil.createURI( location ));
            Attribute responseLocation = startElement.getAttributeByName( new QName( JBossSAMLConstants.RESPONSE_LOCATION.get() ));
            if( responseLocation != null )
            {
               endpoint.setResponseLocation( NetworkUtil.createURI( StaxParserUtil.getAttributeValue( responseLocation )));
            } 
            
            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate( endElement, JBossSAMLConstants.SINGLE_SIGNON_SERVICE.get() );
            
            idpSSODescriptor.addSingleSignOnService( endpoint );
         }
         else if (JBossSAMLConstants.NAMEID_FORMAT.get().equalsIgnoreCase( localPart ))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            idpSSODescriptor.addNameIDFormat( StaxParserUtil.getElementText(xmlEventReader) ); 
         }
         else if (JBossSAMLConstants.ATTRIBUTE.get().equalsIgnoreCase( localPart ))
         {
            AttributeType attribute = SAMLParserUtil.parseAttribute(xmlEventReader);
            idpSSODescriptor.addAttribute(attribute);  
         }
         else 
            throw new RuntimeException( "Unknown " + localPart );
         
      }
      return idpSSODescriptor;
   }
   
   private AttributeAuthorityDescriptorType parseAttributeAuthorityDescriptor( XMLEventReader xmlEventReader ) throws ParsingException
   {
      StartElement startElement = StaxParserUtil.getNextStartElement( xmlEventReader );
      StaxParserUtil.validate(startElement, JBossSAMLConstants.ATTRIBUTE_AUTHORITY_DESCRIPTOR.get() );
      List<String> protocolEnum = SAMLParserUtil.parseProtocolEnumeration(startElement);
      AttributeAuthorityDescriptorType attributeAuthority = new AttributeAuthorityDescriptorType( protocolEnum );
      
      while( xmlEventReader.hasNext() )
      {
         XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if( xmlEvent instanceof EndElement )
         {
            EndElement end = StaxParserUtil.getNextEndElement(xmlEventReader); 
            StaxParserUtil.validate( end , JBossSAMLConstants.ATTRIBUTE_AUTHORITY_DESCRIPTOR.get() );
            break;
         }
         
         startElement = (StartElement) xmlEvent; 
         String localPart = startElement.getName().getLocalPart();
         
         if( JBossSAMLConstants.ATTRIBUTE_SERVICE.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute bindingAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.BINDING.get() ) );
            String binding = StaxParserUtil.getAttributeValue(bindingAttr);
            
            Attribute locationAttr = startElement.getAttributeByName( new QName( JBossSAMLConstants.LOCATION.get() ) );
            String location = StaxParserUtil.getAttributeValue( locationAttr );
            
            IndexedEndpointType endpoint = new IndexedEndpointType( NetworkUtil.createURI( binding ), 
                  NetworkUtil.createURI( location )); 
            
            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate( endElement, JBossSAMLConstants.ATTRIBUTE_SERVICE.get() );
            
            attributeAuthority.addAttributeService( endpoint );
         }  
         else if (JBossSAMLConstants.KEY_DESCRIPTOR.get().equalsIgnoreCase( localPart ))
         {
            KeyDescriptorType keyDescriptor = new KeyDescriptorType();
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
             
            Element key = StaxParserUtil.getDOMElement(xmlEventReader);
            keyDescriptor.setKeyInfo( key );
            
            EndElement endElement = StaxParserUtil.getNextEndElement(xmlEventReader);
            StaxParserUtil.validate( endElement, JBossSAMLConstants.KEY_DESCRIPTOR.get() );
            
            attributeAuthority.addKeyDescriptor( keyDescriptor );  
         }
         else if (JBossSAMLConstants.NAMEID_FORMAT.get().equalsIgnoreCase( localPart ))
         {
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            attributeAuthority.addNameIDFormat( StaxParserUtil.getElementText(xmlEventReader) ); 
         }
         else 
            throw new RuntimeException( "Unknown " + localPart );
         
      }
      return attributeAuthority;
   }
   
   private OrganizationType parseOrganization( XMLEventReader xmlEventReader ) throws ParsingException
   {
      StartElement startElement = StaxParserUtil.getNextStartElement( xmlEventReader );
      StaxParserUtil.validate(startElement, JBossSAMLConstants.ORGANIZATION.get() );

      OrganizationType org = new OrganizationType();
      
      while( xmlEventReader.hasNext() )
      {
         XMLEvent xmlEvent = StaxParserUtil.peek(xmlEventReader);
         if( xmlEvent instanceof EndElement )
         {
            EndElement end = StaxParserUtil.getNextEndElement(xmlEventReader); 
            StaxParserUtil.validate( end , JBossSAMLConstants.ORGANIZATION.get() );
            break;
         }
         
         startElement = (StartElement) xmlEvent; 
         String localPart = startElement.getName().getLocalPart();
         
         if( JBossSAMLConstants.ORGANIZATION_NAME.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute lang = startElement.getAttributeByName( new QName( JBossSAMLURIConstants.XML.get(), "lang" ));
            String langVal = StaxParserUtil.getAttributeValue(lang);
            LocalizedNameType localName = new LocalizedNameType(langVal);
            localName.setValue( StaxParserUtil.getElementText(xmlEventReader));
            org.addOrganizationName(localName);  
         }  
         else if( JBossSAMLConstants.ORGANIZATION_DISPLAY_NAME.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute lang = startElement.getAttributeByName( new QName( JBossSAMLURIConstants.XML.get(), "lang" ));
            String langVal = StaxParserUtil.getAttributeValue(lang);
            LocalizedNameType localName = new LocalizedNameType(langVal);
            localName.setValue( StaxParserUtil.getElementText(xmlEventReader));
            org.addOrganizationDisplayName( localName ) ;  
         }
         else if( JBossSAMLConstants.ORGANIZATION_URL.get().equals( localPart ))
         { 
            startElement = StaxParserUtil.getNextStartElement(xmlEventReader);
            Attribute lang = startElement.getAttributeByName( new QName( JBossSAMLURIConstants.XML.get(), "lang" ));
            String langVal = StaxParserUtil.getAttributeValue(lang);
            LocalizedURIType localName = new LocalizedURIType( langVal );
            localName.setValue( NetworkUtil.createURI( StaxParserUtil.getElementText( xmlEventReader )));
            org.addOrganizationURL( localName ) ;  
         } 
         else 
            throw new RuntimeException( "Unknown " + localPart );
         
      }
      return org;
   }
}