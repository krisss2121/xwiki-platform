/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
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
package org.xwiki.security.authorization.testwikibuilding;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.ObservationManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseObjectReference;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.user.api.XWikiGroupService;

/**
 * Utility class for mocking the objects as needed to test both the old and the caching right service implementations.
 * 
 * @since 4.2
 * @version $Id$
 */
public class LegacyTestWiki extends AbstractTestWiki
{
    private final boolean legacymock;

    private final XWikiContext context;

    private final XWiki xwiki;

    private final Mockery mockery;

    /** State variable for supporting XWikiContext.set/getDatabase. */
    private String currentDatabase;

    /** State variable for XWikiContext.set/getUser. */
    private String currentUsername;

    /** State variable for supporting XWikiContext.get/set("sdoc"). */
    private DocumentReference sdocReference;

    /** State variable for supporting XWikiContext.get/set("doc"). */
    private DocumentReference docReference;

    private String mainWikiName;

    private final DocumentReferenceResolver<String> documentReferenceResolver;

    private final EntityReferenceSerializer<String> entityReferenceSerializer;

    private final ObservationManager observationManager;

    private final Map<String, TestWiki> wikis = new HashMap<String, TestWiki>();

    /**
     * @param mockery The mockery.
     * @param componentManager The component manager.
     * @param testWikiFilename The file name of the test wiki configuration.
     */
    public LegacyTestWiki(Mockery mockery, ComponentManager componentManager, String testWikiFilename,
        boolean legacymock) throws Exception
    {
        this.legacymock = legacymock;
        this.mockery = mockery;

        this.documentReferenceResolver = componentManager.getInstance(DocumentReferenceResolver.TYPE_STRING);
        this.entityReferenceSerializer = componentManager.getInstance(EntityReferenceSerializer.TYPE_STRING);
        this.observationManager = componentManager.getInstance(ObservationManager.class);

        mockery.setImposteriser(ClassImposteriser.INSTANCE);

        context = mockery.mock(XWikiContext.class);

        xwiki = mockery.mock(XWiki.class);

        final XWikiGroupService groupService = mockery.mock(XWikiGroupService.class);

        loadTestWiki(testWikiFilename);

        if (mainWikiName == null) {
            throw new RuntimeException("None of the declared wikis had attribute mainWiki=\"true\"!");
        }

        mockery.checking(new Expectations()
        {
            {
                // Expectations for XWikiContext

                allowing(context).setDatabase(with(any(String.class)));
                will(new CustomAction("set the current database")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        currentDatabase = (String) invocation.getParameter(0);
                        return null;
                    }
                });
                allowing(context).getDatabase();
                will(new CustomAction("return the current database")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        return currentDatabase;
                    }
                });
                allowing(context).get("wiki");
                will(new CustomAction("return the current database")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        return currentDatabase;
                    }
                });
                allowing(context).getWiki();
                will(returnValue(xwiki));
                allowing(context).getMainXWiki();
                will(returnValue(mainWikiName));
                allowing(context).get("grouplist");
                will(returnValue(null));
                allowing(context).put(with(equal("grouplist")), with(anything()));
                allowing(context).getUserReference();
                will(new CustomAction("return the current username")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        return documentReferenceResolver.resolve(currentUsername,
                            documentReferenceResolver.resolve(currentDatabase + ":Main.WebHome"));
                    }
                });
                allowing(context).get("sdoc");
                will(new CustomAction("return the current sdoc")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        if (sdocReference == null) {
                            return null;
                        }
                        return getDocument(sdocReference);
                    }
                });
                allowing(context).getDoc();
                will(new CustomAction("return the current context document")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        if (docReference == null) {
                            return null;
                        }
                        return getDocument(docReference);
                    }
                });
                allowing(context).hasDroppedPermissions();
                will(returnValue(false));

                // Expectations for XWiki

                allowing(xwiki).isReadOnly();
                will(new CustomAction("indicate wether the wiki is read only")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        return isReadOnly(currentDatabase);
                    }
                });
                allowing(xwiki).getMaxRecursiveSpaceChecks(with(any(XWikiContext.class)));
                will(returnValue(100));
                allowing(xwiki).getDocument(with(any(DocumentReference.class)), with(any(XWikiContext.class)));
                will(new CustomAction("return a mocked document")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        DocumentReference documentReference = (DocumentReference) invocation.getParameter(0);
                        return getDocument(documentReference);
                    }
                });
                allowing(xwiki).getDocument(with(any(EntityReference.class)), with(any(XWikiContext.class)));
                will(new CustomAction("return a mocked document")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        EntityReference documentReference = (EntityReference) invocation.getParameter(0);
                        return getDocument(documentReference, (XWikiContext) invocation.getParameter(1));
                    }
                });
                allowing(xwiki).getWikiOwner(with(aNonNull(String.class)), with(any(XWikiContext.class)));
                will(new CustomAction("return the wiki owner")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        String wikiName = (String) invocation.getParameter(0);
                        TestWiki wiki = wikis.get(wikiName);
                        return wiki.getOwner();
                    }
                });
                allowing(xwiki).getGroupService(with(any(XWikiContext.class)));
                will(returnValue(groupService));

                // Expectations for XWikiGroupService

                allowing(groupService).getAllGroupsReferencesForMember(with(any(DocumentReference.class)),
                    with(any(Integer.class)), with(any(Integer.class)), with(any(XWikiContext.class)));
                will(new CustomAction("return a collection of member profile document references")
                {
                    @Override
                    public Object invoke(Invocation invocation)
                    {
                        return getAllGroupReferences((DocumentReference) invocation.getParameter(0));
                    }
                });

                // Expectations needed for old implementation

                allowing(xwiki).getXWikiPreference(with(any(String.class)), with(equal("")),
                    with(any(XWikiContext.class)));
                will(returnValue("false"));
                allowing(xwiki)
                    .getXWikiPreferenceAsInt(with(any(String.class)), with(0), with(any(XWikiContext.class)));
                will(returnValue(0));
                allowing(xwiki).getSpacePreference(with(any(String.class)), with(equal("")),
                    with(any(XWikiContext.class)));
                will(returnValue("false"));
                allowing(xwiki)
                    .getSpacePreferenceAsInt(with(any(String.class)), with(0), with(any(XWikiContext.class)));
                will(returnValue(0));

            }
        });
        
        if (legacymock) {
            mockery.checking(new Expectations()
            {
                {
                    allowing(context).getUser();
                    will(new CustomAction("return the current username")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return currentUsername;
                        }
                    });
                    allowing(xwiki).getDocument(with(any(String.class)), with(any(XWikiContext.class)));
                    will(new CustomAction("return a mocked document")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            String documentName = (String) invocation.getParameter(0);
                            return getDocument(documentName);
                        }
                    });
                    allowing(xwiki).getDocument(with(any(String.class)), with(any(String.class)),
                        with(any(XWikiContext.class)));
                    will(new CustomAction("return a mocked document")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            String spaceName = (String) invocation.getParameter(0);
                            String documentName = (String) invocation.getParameter(1);
                            return getDocument(spaceName, documentName);
                        }
                    });
                    allowing(xwiki).isVirtualMode();
                    will(returnValue(wikis.size() > 1));
                }
            });
        }

    }

    @Override
    public HasWikiContents addWiki(String name, String owner, boolean isMainWiki, boolean isReadOnly)
    {

        if (isMainWiki) {
            if (mainWikiName != null) {
                throw new RuntimeException("Only one wiki can be the main wiki!");
            }

            mainWikiName = name;
        }

        return mockWiki(name, owner, isReadOnly);
    }

    public void setUser(String username)
    {
        this.currentUsername = username;
    }

    public void setSdoc(String sdocFullname)
    {
        if (sdocFullname != null) {
            sdocReference = documentReferenceResolver.resolve(sdocFullname, currentDatabase);
        } else {
            sdocReference = null;
        }
    }

    public void setDoc(String docFullname)
    {
        if (docFullname != null) {
            docReference = documentReferenceResolver.resolve(docFullname, currentDatabase);
        } else {
            docReference = null;
        }
    }

    private TestWiki mockWiki(String name, String owner, boolean isReadOnly)
    {
        TestWiki wiki = wikis.get(name);

        if (wiki == null) {
            wiki = new TestWiki(name, owner, isReadOnly);
            wikis.put(name, wiki);
        }

        return wiki;
    }

    private Collection<DocumentReference> getAllGroupReferences(final DocumentReference userReference)
    {
        final TestWiki wiki = wikis.get(context.getDatabase());

        if (wiki == null) {
            return Collections.<DocumentReference> emptySet();
        }

        String userWiki = userReference.getWikiReference().getName();

        final Collection<String> groupNames =
            wiki.getGroupsForUser(wiki.getName().equals(userWiki) ? userReference.getName()
                : this.entityReferenceSerializer.serialize(userReference));

        final SpaceReference userSpaceReference = new SpaceReference("XWiki", new WikiReference(wiki.getName()));

        return new AbstractCollection<DocumentReference>()
        {

            @Override
            public int size()
            {
                return groupNames.size();
            }

            @Override
            public Iterator<DocumentReference> iterator()
            {
                return new Iterator<DocumentReference>()
                {

                    private final Iterator<String> groupNamesIterator = groupNames.iterator();

                    @Override
                    public boolean hasNext()
                    {
                        return groupNamesIterator.hasNext();
                    }

                    @Override
                    public DocumentReference next()
                    {
                        String groupName = groupNamesIterator.next();

                        return documentReferenceResolver.resolve(groupName, userSpaceReference);
                    }

                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Override
    public XWikiContext getXWikiContext()
    {
        return context;
    }

    public boolean isReadOnly(String wikiName)
    {
        return wikis.get(wikiName).isReadOnly();
    }

    private XWikiDocument getDocument(DocumentReference documentReference)
    {
        TestWiki wiki = wikis.get(documentReference.getWikiReference().getName());
        return wiki.getDocument(documentReference);
    }

    private XWikiDocument getDocument(EntityReference entityReference, XWikiContext context)
    {
        return getDocument(new DocumentReference(entityReference.appendParent(new WikiReference(context.getDatabase()))));
    }

    private XWikiDocument getDocument(String name)
    {
        DocumentReference documentReference;

        if (currentDatabase != null) {
            documentReference = documentReferenceResolver.resolve(name, new WikiReference(currentDatabase));
        } else {
            documentReference = documentReferenceResolver.resolve(name);
        }

        TestWiki wiki = wikis.get(documentReference.getWikiReference().getName());

        if (wiki == null) {
            return null;
        }

        return wiki.getDocument(documentReference);
    }

    private XWikiDocument getDocument(String spaceName, String documentName)
    {
        DocumentReference documentReference = new DocumentReference(currentDatabase, spaceName, documentName);

        TestWiki wiki = wikis.get(documentReference.getWikiReference().getName());

        if (wiki == null) {
            return null;
        }

        return wiki.getDocument(documentReference);
    }

    private class TestWiki extends TestAcl implements HasWikiContents
    {

        private final String name;

        private final String owner;

        private final Map<String, TestSpace> spaces = new HashMap<String, TestSpace>();

        private final Set<String> users = new HashSet<String>();

        private final Map<String, Set<String>> groups = new HashMap<String, Set<String>>();

        private final Map<String, Set<String>> groupsForUser = new HashMap<String, Set<String>>();

        private final boolean isReadOnly;

        TestWiki(String name, String owner, boolean isReadOnly)
        {
            this.name = name;
            this.owner = owner;
            this.isReadOnly = isReadOnly;

            // The XWikiPreferences document always exist (unless explicitly deleted!) since it will be created on
            // application startup or at wiki creation.
            mockDocument("XWiki", "XWikiPreferences", "XWiki.Admin", false);
        }

        @Override
        public void addUser(String name)
        {
            users.add(name);
            if (groupsForUser.get(name) == null) {
                groupsForUser.put(name, new HashSet<String>());
            }

            TestSpace testSpace = mockSpace("XWiki");

            UserTestDocument userTestDocument = new UserTestDocument(name, testSpace, null, false);

            testSpace.documents.put(name, userTestDocument);
        }

        @Override
        public HasUsers addGroup(final String groupName)
        {
            Set<String> groupMembers = groups.get(groupName);

            if (groupMembers == null) {
                groupMembers = new HashSet<String>();
                groups.put(groupName, groupMembers);
            }

            TestSpace testSpace = mockSpace("XWiki");

            GroupTestDocument groupTestDocument = new GroupTestDocument(groupName, testSpace, null, false);

            testSpace.documents.put(groupName, groupTestDocument);

            return groupTestDocument;

        }

        @Override
        public HasDocuments addSpace(String name)
        {
            return mockSpace(name);
        }

        TestDocument mockDocument(String spaceName, String name, String creator, boolean isNew)
        {
            TestSpace space = mockSpace(spaceName);

            return space.mockDocument(name, creator, isNew);
        }

        TestSpace mockSpace(String name)
        {
            TestSpace space = spaces.get(name);

            if (space == null) {
                space = new TestSpace(name, this);
                spaces.put(name, space);
            }

            return space;
        }

        XWikiDocument removeDocument(DocumentReference documentReference)
        {
            return removeDocument(documentReference.getParent().getName(), documentReference.getName());
        }

        XWikiDocument removeDocument(String spaceName, String documentName)
        {
            TestSpace space = mockSpace(spaceName);

            return space.removeDocument(documentName);
        }

        XWikiDocument getDocument(DocumentReference documentReference)
        {
            TestSpace space = mockSpace(documentReference.getParent().getName());

            return space.getDocument(documentReference);
        }

        TestDocument getTestDocument(String spaceName, String documentName)
        {
            if (spaces.containsKey(spaceName)) {
                TestSpace space = mockSpace(spaceName);

                return space.getTestDocument(documentName);
            }

            return null;
        }

        @Override
        String getName()
        {
            return name;
        }

        String getOwner()
        {
            return owner;
        }

        public boolean isReadOnly()
        {
            return isReadOnly;
        }

        Collection<String> getGroupsForUser(String name)
        {
            Set<String> groups = groupsForUser.get(name);
            return groups == null ? Collections.<String> emptySet() : groups;
        }

        void notifyDeleteDocument(XWikiDocument document)
        {
            XWikiDocument newDocument = new XWikiDocument(document.getDocumentReference());
            newDocument.setOriginalDocument(document);

            // Send event
            observationManager.notify(new DocumentDeletedEvent(newDocument.getDocumentReference()), newDocument,
                context);
        }

        void deleteUser(String userName)
        {
            if (users.contains(userName)) {
                users.remove(userName);
                groupsForUser.remove(userName);
                for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
                    entry.getValue().remove(userName);
                    ((GroupTestDocument) getTestDocument("XWiki", entry.getKey())).removeUser(userName);
                }

                // Make sure user document is removed
                XWikiDocument userDocument = removeDocument("XWiki", userName);

                // Send event
                notifyDeleteDocument(userDocument);
            }
        }

        void deleteGroup(String groupName)
        {
            if (groups.containsKey(groupName)) {
                groups.remove(groupName);
                for (Set<String> groups : groupsForUser.values()) {
                    groups.remove(groupName);
                }

                // Make sure group document is removed
                XWikiDocument groupDocument = removeDocument("XWiki", groupName);

                // Send event
                notifyDeleteDocument(groupDocument);
            }
        }

        void deleteDocument(DocumentReference documentReference)
        {
            XWikiDocument document = removeDocument(documentReference);

            if (document != null) {
                // Send event
                notifyDeleteDocument(document);
            }
        }
    }

    private class TestSpace extends TestAcl implements HasDocuments
    {
        private final String name;

        private final TestWiki wiki;

        private final Map<String, TestDocument> documents = new HashMap<String, TestDocument>();

        public TestSpace(String name, TestWiki wiki)
        {
            this.name = name;
            this.wiki = wiki;
        }

        @Override
        public HasAcl addDocument(String name, String creator)
        {
            return mockDocument(name, creator, false);
        }

        @Override
        public XWikiDocument removeDocument(String name)
        {
            TestDocument document = documents.get(name);

            if (document == null) {
                return null;
            }

            return document.getDocument();
        }

        TestDocument mockDocument(String name, String creator, boolean isNew)
        {
            TestDocument document = getTestDocument(name);

            if (document == null) {
                if (creator == null) {
                    creator = "XWiki.Admin";
                }
                document = new TestDocument(name, this, creator, isNew);
                documents.put(name, document);
            }

            return document;
        }

        XWikiDocument getDocument(DocumentReference documentReference)
        {
            TestDocument document = mockDocument(documentReference.getName(), currentUsername, true);

            return document.getDocument();
        }

        public TestDocument getTestDocument(String name)
        {
            return documents.get(name);
        }

        @Override
        String getName()
        {
            return name;
        }

        TestWiki getWiki()
        {
            return wiki;
        }
    }

    private class UserTestDocument extends TestDocument
    {
        private BaseObject userObject;

        UserTestDocument(String name, TestSpace space, String creator, Boolean isNew)
        {
            super(name, space, creator, isNew);

            this.userObject = mockUserBaseObject();

            mockery.checking(new Expectations()
            {
                {
                    allowing(mockedDocument).getXObject(with(equal(new LocalDocumentReference("XWiki", "XWikiUsers"))));
                    will(new CustomAction("return the user object")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return userObject;
                        }
                    });
                }
            });
        }

        private BaseObject mockUserBaseObject()
        {
            ++objectNumber;

            final BaseObject userBaseObject = mockery.mock(BaseObject.class, getName() + objectNumber);

            return userBaseObject;
        }

        @Override
        public Map<DocumentReference, List<BaseObject>> getLegacyXObjects()
        {
            Map<DocumentReference, List<BaseObject>> xobjects = super.getLegacyXObjects();

            xobjects.put(new DocumentReference(this.space.wiki.getName(), "XWiki", "XWikiUsers"),
                Arrays.asList(this.userObject));

            return xobjects;
        }
    }

    private class GroupTestDocument extends TestDocument implements HasUsers
    {
        private Map<String, BaseObject> memberObjects = new LinkedHashMap<String, BaseObject>();

        GroupTestDocument(String name, TestSpace space, String creator, Boolean isNew)
        {
            super(name, space, creator, isNew);

            mockery.checking(new Expectations()
            {
                {
                    allowing(mockedDocument).getXObjects(
                        with(equal(new LocalDocumentReference("XWiki", "XWikiGroups"))));
                    will(new CustomAction("return a vector of group members")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return new Vector<BaseObject>(memberObjects.values());
                        }
                    });
                }
            });
        }

        private BaseObject mockGroupBaseObject(String user)
        {
            ++objectNumber;

            final BaseObject userBaseObject = mockery.mock(BaseObject.class, getName() + objectNumber);

            return userBaseObject;
        }

        @Override
        public Map<DocumentReference, List<BaseObject>> getLegacyXObjects()
        {
            Map<DocumentReference, List<BaseObject>> xobjects = super.getLegacyXObjects();

            xobjects.put(new DocumentReference(this.space.wiki.getName(), "XWiki", "XWikiUsers"),
                new ArrayList<BaseObject>(this.memberObjects.values()));

            return xobjects;
        }

        @Override
        public void addUser(String userName)
        {
            TestWiki testWiki = space.getWiki();

            Set<String> groups = testWiki.groupsForUser.get(userName);
            if (groups == null) {
                groups = new HashSet<String>();
                testWiki.groupsForUser.put(userName, groups);
            }
            groups.add(getName());

            testWiki.groups.get(getName()).add(userName);

            this.memberObjects.put(userName, mockGroupBaseObject(userName));
        }

        public void removeUser(String userName)
        {
            this.memberObjects.remove(userName);
        }
    }

    private class TestDocument extends TestAcl
    {
        protected final XWikiDocument mockedDocument;

        protected final TestSpace space;

        protected final String name;

        protected final String creator;

        private List<BaseObject> globalRights;

        private List<BaseObject> documentRights;

        TestDocument(final String name, final TestSpace space, final String creator, final Boolean isNew)
        {
            this.space = space;
            this.name = name;
            this.creator = creator;

            mockedDocument =
                mockery.mock(XWikiDocument.class,
                    new Formatter().format("%s:%s.%s", getSpace().getWiki().getName(), getSpace().getName(), getName())
                        .toString());

            final DocumentReference documentReference =
                new DocumentReference(getSpace().getWiki().getName(), getSpace().getName(), getName());

            mockery.checking(new Expectations()
            {
                {
                    allowing(mockedDocument).getXObjects(
                        with(equal(new LocalDocumentReference("XWiki", "XWikiRights"))));
                    will(new CustomAction("return a vector of rights")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return getLegacyDocumentRights();
                        }
                    });
                    allowing(mockedDocument).getXObjects(
                        with(equal(new LocalDocumentReference("XWiki", "XWikiGlobalRights"))));
                    will(new CustomAction("return a vector of rights")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return getLegacyGlobalRights();
                        }
                    });
                    allowing(mockedDocument).getXObjects(
                        new DocumentReference(space.wiki.getName(), "XWiki", "XWikiRights"));
                    will(new CustomAction("return a vector of rights")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return getLegacyDocumentRights();
                        }
                    });
                    allowing(mockedDocument).getXObjects(
                        new DocumentReference(space.wiki.getName(), "XWiki", "XWikiGlobalRights"));
                    will(new CustomAction("return a vector of rights")
                    {
                        @Override
                        public Object invoke(Invocation invocation)
                        {
                            return getLegacyGlobalRights();
                        }
                    });
                    allowing(mockedDocument).getCreatorReference();
                    will(returnValue(documentReferenceResolver.resolve(TestDocument.this.creator, documentReference)));
                    allowing(mockedDocument).getDocumentReference();
                    will(returnValue(documentReference));
                    allowing(mockedDocument).isNew();
                    will(returnValue(isNew));
                }
            });

            if (legacymock) {
                mockery.checking(new Expectations()
                {
                    {
                        allowing(mockedDocument).getObjects("XWiki.XWikiGlobalRights");
                        will(new CustomAction("return a vector of global rights")
                        {
                            @Override
                            public Object invoke(Invocation invocation)
                            {
                                return getLegacyGlobalRights();
                            }
                        });
                        allowing(mockedDocument).getObjects("XWiki.XWikiRights");
                        will(new CustomAction("return a vector of document rights")
                        {
                            @Override
                            public Object invoke(Invocation invocation)
                            {
                                return getLegacyDocumentRights();
                            }
                        });
                        allowing(mockedDocument).getWikiName();
                        will(returnValue(getSpace().getWiki().getName()));
                        allowing(mockedDocument).getDatabase();
                        will(returnValue(getSpace().getWiki().getName()));
                        allowing(mockedDocument).getSpace();
                        will(returnValue(getSpace().getName()));
                    }
                });
            }
        }

        public XWikiDocument getDocument()
        {
            return mockedDocument;
        }

        public Vector<BaseObject> getLegacyGlobalRights()
        {
            Vector<BaseObject> baseObjects = new Vector<BaseObject>();
            if ("XWikiPreferences".equals(getName())) {
                return getSpace().getWiki().getLegacyRightObjects();
            } else if ("WebPreferences".equals(getName())) {
                return getSpace().getLegacyRightObjects();
            }
            return null;
        }

        public Vector<BaseObject> getLegacyDocumentRights()
        {
            return getLegacyRightObjects();
        }

        public Map<DocumentReference, List<BaseObject>> getLegacyXObjects()
        {
            Map<DocumentReference, List<BaseObject>> objects = new HashMap<DocumentReference, List<BaseObject>>();

            Vector<BaseObject> globalRights = getLegacyGlobalRights();
            if (globalRights != null) {
                objects.put(new DocumentReference(this.space.wiki.getName(), "XWiki", "XWikiGlobalRights"),
                    globalRights);
            }
            Vector<BaseObject> rights = getLegacyDocumentRights();
            if (globalRights != null) {
                objects.put(new DocumentReference(this.space.wiki.getName(), "XWiki", "XWikiRights"), rights);
            }

            return objects;
        }

        @Override
        String getName()
        {
            return name;
        }

        TestSpace getSpace()
        {
            return space;
        }
    }

    public static int objectNumber = 0;

    private abstract class TestAcl implements HasAcl
    {
        private final Map<String, String> allowUser = new HashMap<String, String>();

        private final Map<String, String> denyUser = new HashMap<String, String>();

        private final Map<String, String> allowGroup = new HashMap<String, String>();

        private final Map<String, String> denyGroup = new HashMap<String, String>();

        private List<BaseObject> mockedObjects = null;

        @Override
        public void addAllowUser(String name, String type)
        {
            allowUser.put(name, type);
        }

        @Override
        public void addDenyUser(String name, String type)
        {
            denyUser.put(name, type);
        }

        @Override
        public void addAllowGroup(String name, String type)
        {
            allowGroup.put(name, type);
        }

        @Override
        public void addDenyGroup(String name, String type)
        {
            denyGroup.put(name, type);
        }

        abstract String getName();

        public Vector<BaseObject> getLegacyRightObjects()
        {
            if (mockedObjects == null) {
                mockedObjects = new ArrayList<BaseObject>();
                for (String name : allowUser.keySet()) {
                    mockedObjects.add(mockRightBaseObject(name, allowUser.get(name), true, true));
                }
                for (String name : denyUser.keySet()) {
                    mockedObjects.add(mockRightBaseObject(name, denyUser.get(name), true, false));
                }
                for (String name : allowGroup.keySet()) {
                    mockedObjects.add(mockRightBaseObject(name, allowGroup.get(name), false, true));
                }
                for (String name : denyGroup.keySet()) {
                    mockedObjects.add(mockRightBaseObject(name, denyGroup.get(name), false, false));
                }
            }

            return mockedObjects.size() == 0 ? null : new Vector<BaseObject>(mockedObjects);
        }

        private BaseObject mockRightBaseObject(final String name, final String type, final boolean isUser,
            final boolean allow)
        {
            objectNumber++;

            final BaseObject rightBaseObjects = mockery.mock(BaseObject.class, getName() + objectNumber);

            final BaseProperty<BaseObjectReference> usersProperty =
                mockery.mock(BaseProperty.class, getName() + objectNumber + "users");
            final BaseProperty<BaseObjectReference> groupsProperty =
                mockery.mock(BaseProperty.class, getName() + objectNumber + "groups");
            final BaseProperty<BaseObjectReference> levelsProperty =
                mockery.mock(BaseProperty.class, getName() + objectNumber + "levels");

            final List<String> users = isUser ? Arrays.<String> asList(name) : Collections.<String> emptyList();
            final String usersString = users.isEmpty() ? "" : name;
            final List<String> groups = isUser ? Collections.<String> emptyList() : Arrays.<String> asList(name);
            final String groupsString = groups.isEmpty() ? "" : name;
            final List<String> levels = Arrays.<String> asList(type);
            final String levelsString = type;

            mockery.checking(new Expectations()
            {
                {
                    allowing(rightBaseObjects).getIntValue("allow");
                    will(returnValue(allow ? 1 : 0));

                    // New security module
                    allowing(rightBaseObjects).safeget("users");
                    will(returnValue(usersProperty));
                    allowing(usersProperty).getValue();
                    will(returnValue(users));
                    allowing(rightBaseObjects).safeget("groups");
                    will(returnValue(groupsProperty));
                    allowing(groupsProperty).getValue();
                    will(returnValue(groups));
                    allowing(rightBaseObjects).safeget("levels");
                    will(returnValue(levelsProperty));
                    allowing(levelsProperty).getValue();
                    will(returnValue(levels));
                }
            });

            if (legacymock) {
                mockery.checking(new Expectations()
                {
                    {
                        // Old security module
                        allowing(rightBaseObjects).getStringValue("users");
                        will(returnValue(usersString));
                        allowing(rightBaseObjects).getStringValue("groups");
                        will(returnValue(groupsString));
                        allowing(rightBaseObjects).getStringValue("levels");
                        will(returnValue(levelsString));
                    }
                });
            }

            return rightBaseObjects;
        }
    }

    // Modifications

    public void deleteUser(String userName, String wikiName)
    {
        final TestWiki wiki = this.wikis.get(wikiName);

        wiki.deleteUser(userName);
    }

    public void deleteGroup(String groupName, String wikiName)
    {
        final TestWiki wiki = wikis.get(wikiName);

        wiki.deleteGroup(groupName);
    }

    public void deleteDocument(DocumentReference documentReference)
    {
        final TestWiki wiki = wikis.get(documentReference.getWikiReference().getName());

        wiki.deleteDocument(documentReference);
    }
}
