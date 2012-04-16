/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.relationship;

import java.util.ArrayList;
import java.util.List;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.identity.IdentityManager;
import org.exoplatform.social.core.identity.impl.organization.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.relationship.lifecycle.RelationshipLifeCycle;
import org.exoplatform.social.core.relationship.lifecycle.RelationshipListenerPlugin;
import org.exoplatform.social.core.relationship.storage.JCRStorage;
import org.exoplatform.social.relationship.spi.RelationshipListener;
import org.exoplatform.social.jcr.SocialDataLocation;

/**
 * The Class RelationshipManager.
 */
public class RelationshipManager {
  
  /** The storage. */
  private JCRStorage storage;

  /**
   * lifecycle of a relationship
   */
  private RelationshipLifeCycle lifeCycle = new RelationshipLifeCycle();

  /**
   * Instantiates a new relationship manager.
   * 
   * @param dataLocation the data location
   * @param im the im
   * @throws Exception the exception
   */
  public RelationshipManager(SocialDataLocation dataLocation, IdentityManager im) throws Exception {
    this.storage = new JCRStorage(dataLocation, im);
  }

  /**
   * Gets the by id.
   * 
   * @param id the id
   * @return the by id
   * @throws Exception the exception
   */
  public Relationship getById(String id) throws Exception {
    return this.storage.getRelationship(id);
  }
  
  /**
   * Creates a connection invitation between 2 identities
   * @param currIdentity inviter
   * @param requestedIdentity invitee
   * @return a PENDING relation
   * @throws Exception
   */
  public Relationship invite(Identity currIdentity, Identity requestedIdentity) throws Exception {
    Relationship rel = create(currIdentity, requestedIdentity);
    rel.setStatus(Relationship.Type.PENDING);
    save(rel);
    lifeCycle.relationshipRequested(this, rel);
    return rel;
  }
  
  

  /**
   * mark a relationship as confirmed.
   * 
   * @param relationship the relationship
   * @throws Exception the exception
   */
  public void confirm(Relationship relationship) throws Exception {
    relationship.setStatus(Relationship.Type.CONFIRM);
    for (Property prop : relationship.getProperties()) {
      prop.setStatus(Relationship.Type.CONFIRM);
    }
    save(relationship);
    lifeCycle.relationshipConfirmed(this, relationship);
  }
  
  public void deny(Relationship relationship) throws Exception {
    storage.removeRelationship(relationship);
    lifeCycle.relationshipDenied(this, relationship);
  }

  /**
   * remove a relationship.
   * 
   * @param relationship the relationship
   * @throws Exception the exception
   */
  public void remove(Relationship relationship) throws Exception {
    storage.removeRelationship(relationship);
    lifeCycle.relationshipRemoved(this, relationship);
  }

  /**
   * mark a relationship as ignored.
   * 
   * @param relationship the relationship
   * @throws Exception the exception
   */
  public void ignore(Relationship relationship) throws Exception {
    relationship.setStatus(Relationship.Type.IGNORE);
    for (Property prop : relationship.getProperties()) {
      prop.setStatus(Relationship.Type.IGNORE);
    }
    save(relationship);
    lifeCycle.relationshipIgnored(this, relationship);
  }

  /**
   * return all the public relationship.
   * 
   * @param identity the identity
   * @return the public relation
   * @throws Exception the exception
   * @return
   */
  public List<Identity> getPublicRelation(Identity identity) throws Exception {
    List<Identity> ids = new ArrayList<Identity>();
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    IdentityManager im = (IdentityManager) container.getComponentInstanceOfType(IdentityManager.class);
    List<Identity> allIds = im.getIdentities(OrganizationIdentityProvider.NAME);
    for (Identity id : allIds) {
      if (!(id.getId().equals(identity.getId())) && (getRelationship(identity, id) == null)) {
        ids.add(id);
      }
    }
    
    return ids;
  }
  
  /**
   * return all the pending relationship: sent and received.
   * 
   * @param identity the identity
   * @return the pending
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> getPending(Identity identity) throws Exception {
    List<Relationship> rels = get(identity);
    List<Relationship> pendingRel = new ArrayList<Relationship>();
    for (Relationship rel : rels) {
      if (rel.getStatus() == Relationship.Type.PENDING) {
        pendingRel.add(rel);
      } else {
        if (rel.getProperties(Relationship.Type.PENDING).size() > 0)
          pendingRel.add(rel);
      }
    }
    return pendingRel;
  }

  /**
   * if toConfirm is true, it return list of pending relationship received not
   * confirmed if toConfirm is false, it return list of relationship sent not
   * confirmed yet.
   * 
   * @param identity the identity
   * @param toConfirm the to confirm
   * @return the pending
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> getPending(Identity identity, boolean toConfirm) throws Exception {
    List<Relationship> rels = get(identity);
    List<Relationship> pendingRel = new ArrayList<Relationship>();
    if(toConfirm) {
     for(Relationship rel : rels) {
       if(getRelationshipStatus(rel, identity).equals(Relationship.Type.PENDING))
         pendingRel.add(rel);
     }
     return pendingRel;
    }
    for (Relationship relationship : rels) {
      if(getRelationshipStatus(relationship, identity).equals(Relationship.Type.REQUIRE_VALIDATION))
        pendingRel.add(relationship);
    }
    return pendingRel;
  }

  /**
   * Get pending relations in 2 case:
   * - if toConfirm is true, it return list of pending relationship received not confirmed
   * - if toConfirm is false, it return list of relationship sent not confirmed yet.
   * 
   * @param currIdentity the curr identity
   * @param identities the identities
   * @param toConfirm the to confirm
   * @return the pending
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> getPending(Identity currIdentity, List<Identity> identities, boolean toConfirm) throws Exception {
    List<Relationship> pendingRels = getPending(currIdentity, true);
    List<Relationship> invitedRels = getPending(currIdentity, false);
    List<Relationship> pendingRel = new ArrayList<Relationship>();
    if (toConfirm) {
      for (Identity id : identities) {
        for (Relationship rel : pendingRels) {
          if (rel.getIdentity2().getRemoteId().equals(id.getRemoteId())) {
            pendingRel.add(rel);
            break;
          }
        }
      }
      
      return pendingRel;
    }
    
    for (Identity id : identities) {
      for (Relationship rel : invitedRels) {
        if (rel.getIdentity1().getRemoteId().equals(id.getRemoteId())) {
          pendingRel.add(rel);
          break;
        }
      }
    }
    
    return pendingRel;
  }
  
  /**
   * Get contacts that match the search result.
   * 
   * @param currIdentity the curr identity
   * @param identities the identities
   * @return the contacts
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> getContacts(Identity currIdentity, List<Identity> identities) throws Exception {
    List<Relationship> contacts = getContacts(currIdentity);
    List<Relationship> relations = new ArrayList<Relationship>();
    Identity identityRel = null;
    for (Identity id : identities) {
      for (Relationship contact : contacts) {
        identityRel = contact.getIdentity1().getRemoteId().equals(currIdentity.getRemoteId()) ? contact.getIdentity2() : contact.getIdentity1();  
        if (identityRel.getRemoteId().equals(id.getRemoteId())) {
          relations.add(contact);
          break;
        }
      }
    }
    
    return relations;
  }
  
  /**
   * Gets the contacts.
   * 
   * @param identity the identity
   * @return the contacts
   * @throws Exception the exception
   */
  public List<Relationship> getContacts(Identity identity) throws Exception {
    List<Relationship> rels = get(identity);
    if(rels == null) return null;
    List<Relationship> contacts = new ArrayList<Relationship>();
    for (Relationship rel : rels) {
      if (rel.getStatus() == Relationship.Type.CONFIRM) {
        contacts.add(rel);
      }
    }
    return contacts;
  }

  /**
   * return all the relationship associated with a given identity.
   * 
   * @param id the id
   * @return the list
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> get(Identity id) throws Exception {
    return this.storage.getRelationshipByIdentity(id);
  }

  /**
   * return all the relationship associated with a given identityId.
   * 
   * @param id the id
   * @return the by identity id
   * @throws Exception the exception
   * @return
   */
  public List<Relationship> getByIdentityId(String id) throws Exception {
    return this.storage.getRelationshipByIdentityId(id);
  }

  /**
   * return all the identity associated with a given identity TODO check if the
   * relation has been validated.
   * 
   * @param id the id
   * @return the identities
   * @throws Exception the exception
   * @return
   */
  public List<Identity> getIdentities(Identity id) throws Exception {
    return this.storage.getRelationshipIdentitiesByIdentity(id);
  }

  /**
   * Creates the.
   * 
   * @param id1 the id1
   * @param id2 the id2
   * @return the relationship
   */
  public Relationship create(Identity id1, Identity id2) {
    return new Relationship(id1, id2);
  }

  /**
   * Save.
   * 
   * @param rel the rel
   * @throws Exception the exception
   */
  void save(Relationship rel) throws Exception {
    if (rel.getIdentity1().getId().equals(rel.getIdentity2().getId()))
      throw new Exception("the two identity are the same");
    for (Property prop : rel.getProperties()) {

      // if the initator ID is not in the member of the relationship, we throw
      // an exception
      if (!(prop.getInitiator().getId().equals(rel.getIdentity1().getId()) || prop.getInitiator()
                                                                                  .getId()
                                                                                  .equals(rel.getIdentity2()
                                                                                             .getId()))) {

        throw new Exception("the property initiator is not member of the relationship");
      }
    }
    this.storage.saveRelationship(rel);
  }

  /**
   * Find route.
   * 
   * @param id1 the id1
   * @param id2 the id2
   * @return the list
   */
  public List findRoute(Identity id1, Identity id2) {
    return null;
  }

  /**
   * Gets the relationship.
   * 
   * @param id1 the id1
   * @param id2 the id2
   * @return the relationship
   * @throws Exception the exception
   */
  public Relationship getRelationship(Identity id1, Identity id2) throws Exception {
    List<Relationship> rels = get(id1);
    String sId2 = id2.getId();
    for (Relationship rel : rels) {
      if (rel.getIdentity1().getId().equals(sId2) || rel.getIdentity2().getId().equals(sId2)) {
        return rel;
      }
    }
    return null;
  }

  // TODO: dang.tung - get relation ship status of one identity in one relation
  // ship.
  /**
   * Gets the relationship status.
   * 
   * @param rel the rel
   * @param id the id
   * @return the relationship status
   */
  public Relationship.Type getRelationshipStatus(Relationship rel, Identity id) {
    if (rel == null)
      return Relationship.Type.ALIEN;
    Identity identity1 = rel.getIdentity1();
    if (rel.getStatus().equals(Relationship.Type.PENDING)) {
      if (identity1.getId().equals(id.getId()))
        return Relationship.Type.PENDING;
      else
        return Relationship.Type.REQUIRE_VALIDATION;
    } else if (rel.getStatus().equals(Relationship.Type.IGNORE)) {
      // TODO need to change in future
      return Relationship.Type.ALIEN;
    }
    return Relationship.Type.CONFIRM;
  }
  
  

  public void registerListener(RelationshipListener listener) {
    lifeCycle.addListener(listener);
  }


  public void unregisterListener(RelationshipListener listener) {
    lifeCycle.removeListener(listener);
  }  
  
  public void addListenerPlugin(RelationshipListenerPlugin plugin) {
    registerListener(plugin);
  }


  
}