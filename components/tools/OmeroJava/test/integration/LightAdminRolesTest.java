/*
 * Copyright (C) 2017 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package integration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import omero.RLong;
import omero.RString;
import omero.RType;
import omero.SecurityViolation;
import omero.ServerError;
import omero.api.IRenderingSettingsPrx;
import omero.api.IScriptPrx;
import omero.api.RawFileStorePrx;
import omero.api.ServiceFactoryPrx;
import omero.cmd.Chown2;
import omero.gateway.util.Requests;
import omero.gateway.util.Requests.Delete2Builder;
import omero.model.AdminPrivilege;
import omero.model.AdminPrivilegeI;
import omero.model.Annotation;
import omero.model.Dataset;
import omero.model.DatasetImageLink;
import omero.model.DatasetImageLinkI;
import omero.model.Experimenter;
import omero.model.ExperimenterGroup;
import omero.model.ExperimenterGroupI;
import omero.model.ExperimenterI;
import omero.model.FileAnnotation;
import omero.model.FileAnnotationI;
import omero.model.IObject;
import omero.model.Image;
import omero.model.ImageAnnotationLink;
import omero.model.ImageAnnotationLinkI;
import omero.model.ImageI;
import omero.model.NamedValue;
import omero.model.OriginalFile;
import omero.model.OriginalFileI;
import omero.model.Permissions;
import omero.model.PermissionsI;
import omero.model.Pixels;
import omero.model.Project;
import omero.model.ProjectDatasetLink;
import omero.model.ProjectDatasetLinkI;
import omero.model.RectangleI;
import omero.model.RenderingDef;
import omero.model.Roi;
import omero.model.RoiI;
import omero.model.Session;
import omero.model.TagAnnotation;
import omero.model.TagAnnotationI;
import omero.model.enums.AdminPrivilegeChgrp;
import omero.model.enums.AdminPrivilegeChown;
import omero.model.enums.AdminPrivilegeDeleteOwned;
import omero.model.enums.AdminPrivilegeDeleteScriptRepo;
import omero.model.enums.AdminPrivilegeModifyGroup;
import omero.model.enums.AdminPrivilegeModifyGroupMembership;
import omero.model.enums.AdminPrivilegeModifyUser;
import omero.model.enums.AdminPrivilegeSudo;
import omero.model.enums.AdminPrivilegeWriteFile;
import omero.model.enums.AdminPrivilegeWriteManagedRepo;
import omero.model.enums.AdminPrivilegeWriteOwned;
import omero.model.enums.AdminPrivilegeWriteScriptRepo;
import omero.sys.EventContext;
import omero.sys.Parameters;
import omero.sys.ParametersI;
import omero.sys.Principal;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests the concrete workflows of the light admins
 * @author p.walczysko@dundee.ac.uk
 * @since 5.4.0
 */
public class LightAdminRolesTest extends RolesTests {

    /**
     * Create a light administrator, with a specific privilege, and log in as them.
     * All the other privileges will be set to false.
     * @param isAdmin if the user should be a member of the <tt>system</tt> group
     * @param permission the privilege that the user should have, or {@code null} if they should have no privileges
     * @return the new user's context
     * @throws Exception if the light administrator could not be created
     */
    private EventContext loginNewAdmin(boolean isAdmin, String permission) throws Exception {
        final EventContext ctx = loginNewAdmin(isAdmin, Arrays.asList(permission));
        return ctx;
    }

    /**
     * Create a light administrator, with a specific list of privileges, and log in as them.
     * All the other privileges will be set to False.
     * @param isAdmin if the user should be a member of the <tt>system</tt> group
     * @param permissions the privileges that the user should have, or {@code null} if they should have no privileges
     * @return the new user's context
     * @throws Exception if the light administrator could not be created
     */
    private EventContext loginNewAdmin(boolean isAdmin, List <String> permissions) throws Exception {
        final EventContext ctx = isAdmin ? newUserInGroup(iAdmin.lookupGroup(roles.systemGroupName), false) : newUserAndGroup("rwr-r-");
        final ServiceFactoryPrx rootSession = root.getSession();
        Experimenter user = new ExperimenterI(ctx.userId, false);
        user = (Experimenter) rootSession.getQueryService().get("Experimenter", ctx.userId);
        final List<AdminPrivilege> privileges = new ArrayList<>();
        rootSession.getAdminService().setAdminPrivileges(user, privileges);
        for (final String permission : permissions) {
            final AdminPrivilege privilege = new AdminPrivilegeI();
            privilege.setValue(omero.rtypes.rstring(permission));
            privileges.add(privilege);
        }
        rootSession.getAdminService().setAdminPrivileges(user, privileges);
        /* avoid old session as privileges are briefly cached */
        loginUser(ctx);
        return ctx;
    }

    /**
     * Sudo to the given user.
     * @param target a user
     * @throws Exception if the sudo could not be performed
     */
    private void sudo(Experimenter target) throws Exception {
        if (!target.isLoaded()) {
            target = iAdmin.getExperimenter(target.getId().getValue());
        }
        sudo(target.getOmeName().getValue());
    }

    /**
     * Annotate image with tag and file annotation and return the annotation objects
     * including the original file of the file annotation and the links
     * @param image the image to be annotated
     * @return the list of the tag, original file of the file annotation, file annotation
     * and the links between the tag and image and the file annotation and image
     * @throws Exception
     */
    private List<IObject> annotateImageWithTagAndFile(Image image) throws Exception {
        TagAnnotation tagAnnotation = new TagAnnotationI();
        tagAnnotation = (TagAnnotation) iUpdate.saveAndReturnObject(tagAnnotation);
        final ImageAnnotationLink tagAnnotationLink = linkImageAnnotation(image, tagAnnotation);
        /* add a file attachment with original file to the imported image.*/
        final ImageAnnotationLink fileAnnotationLink = linkImageAnnotation(image, mmFactory.createFileAnnotation());
        /* link was saved in previous step with the whole graph, including fileAnnotation and original file */
        final FileAnnotation fileAnnotation = (FileAnnotation) fileAnnotationLink.getChild();
        final OriginalFile annotOriginalFile = fileAnnotation.getFile();
        /* make a list of annotation objects in order to simplify checking of owner and group */
        List<IObject> annotOriginalFileAnnotationTagAndLinks = new ArrayList<IObject>();
        annotOriginalFileAnnotationTagAndLinks.addAll(Arrays.asList(annotOriginalFile, fileAnnotation, tagAnnotation,
                tagAnnotationLink, fileAnnotationLink));
        return annotOriginalFileAnnotationTagAndLinks;
    }

    /**
     * Test that a light administrator can create new Project and Dataset
     * and import data on behalf of another user solely with <tt>Sudo</tt> privilege
     * into this Dataset. Further link the Dataset to the Project, check
     * that the link belongs to the user (not to the light admin).
     * All workflows are tested here both when light admin is sudoing
     * and when he/she is not sudoing, except for Link and Import (both tested
     * only when sudoing, as the non-sudoing workflows are too complicated
     * for those two actions and thus covered by separate tests).
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1JX6b9pkPtG-3hZIGSp2bu4WVvNKn6GHqIKdfbbJkiw8/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "isSudoing and WriteOwned privileges cases")
    public void testImporterAsSudoCreateImport(boolean isSudoing, boolean permWriteOwned,
            String groupPermissions) throws Exception {
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        final boolean isExpectSuccessCreate = permWriteOwned || isSudoing;
        /* set up the light admin's permissions for this test */
        List<String> permissions = new ArrayList<String>();
        permissions.add(AdminPrivilegeSudo.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        if (isSudoing) sudo(new ExperimenterI(normalUser.userId, false));

        /* First, create Project and Dataset on behalf of the normalUser
         * in the group of the normalUser in anticipation of importing
         * data for the normalUser in the next step into these containers */
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        Project proj = mmFactory.simpleProject();
        Dataset dat = mmFactory.simpleDataset();
        Project sentProj = null;
        Dataset sentDat = null;
        /* Set the normalUser as the owner of the newly created P/D but do this only
         * when the lightAdmin is not sudoing (if sudoing, this step is not necessary
         * because the created P/D already belongs to the normalUser) */
        if (!isSudoing) {
            proj.getDetails().setOwner(new ExperimenterI(normalUser.userId, false));
            dat.getDetails().setOwner(new ExperimenterI(normalUser.userId, false));
        }
        /* Check that you can save the created Project and Dataset only when the permissions
         * are sufficient.*/
        try {
            sentProj = (Project) iUpdate.saveAndReturnObject(proj);
            sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
            Assert.assertTrue(isExpectSuccessCreate);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessCreate);
        }
        /* Check the owner of the Project and Dataset is the normalUser in case
         * these were created, otherwise they should be null.*/
        if (isExpectSuccessCreate) {
            assertOwnedBy(sentProj, normalUser);
            assertOwnedBy(sentDat, normalUser);
        } else {
            Assert.assertNull(sentProj);
            Assert.assertNull(sentDat);
        }
        /* Finish the test if light admin is not sudoing, the further part
         * of the test deals with the imports. Imports when not sudoing workflows
         * are covered in other tests in this class.*/
        if (!isSudoing) return;

        /* Check that after sudo, the lightAdmin is able to ImportAs and target
         * the import into the just created Dataset.
         * This checks that the lightAdmin can import and write the original file
         * of the imported image on behalf of the normalUser and into the group of normalUser */
        List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
        OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
        Image image = (Image) originalFileAndImage.get(1);

        /* Check that the canLink() method is delivering true on both the Dataset
         * and Project to be linked. As the cases where the light admin was not sudoing
         * are not tested in this part of the test, the canLink() is always true
         * for the owner of the objects (the normalUser as who the light admin is
         * sudoed for).*/
        Assert.assertTrue(getCurrentPermissions(sentProj).canLink());
        Assert.assertTrue(getCurrentPermissions(sentDat).canLink());
        ProjectDatasetLink projectDatasetLink = linkProjectDataset(sentProj, sentDat);

        /* Check the owner of the links between image and Dataset and
         * Dataset and Project is normalUser
         * and the image and its original file are in the group of normalUser.*/
        final IObject imageDatasetLink = iQuery.findByQuery(
                "FROM DatasetImageLink WHERE child.id = :id",
                new ParametersI().addId(image.getId().getValue()));
        assertInGroup(originalFile, normalUser.groupId);
        assertOwnedBy(originalFile, normalUser);
        assertInGroup(image, normalUser.groupId);
        assertOwnedBy(image, normalUser);
        assertOwnedBy(imageDatasetLink, normalUser);
        assertOwnedBy(projectDatasetLink, normalUser);
    }

    /**
     * Test whether a light admin can delete image, Project and Dataset
     * and their respective links belonging to another
     * user. Behaviors of the system are explored when lightAdmin
     * is and is not using <tt>Sudo</tt> privilege
     * for this action.
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permDeleteOwned if to test a user who has the <tt>DeleteOwned</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1SRWiFJs7oIYJCSg8XpfeW0QyOPwbrSnAbXL_FaKF0I4/edit#slide=id.p7</a>
     */
   @Test(dataProvider = "isSudoing and Delete privileges cases")
   public void testDelete(boolean isSudoing, boolean permDeleteOwned,
           String groupPermissions) throws Exception {
       /* Only DeleteOwned permission is needed for deletion of links, Dataset
        * and image (with original file) when not sudoing. When sudoing, no other
        * permission is needed.*/
       boolean deletePassing = permDeleteOwned || isSudoing;
       final EventContext normalUser = newUserAndGroup(groupPermissions);
       /* Set up the light admin's permissions for this test */
       List<String> permissions = new ArrayList<String>();
       permissions.add(AdminPrivilegeSudo.value);
       if (permDeleteOwned) permissions.add(AdminPrivilegeDeleteOwned.value);
       final EventContext lightAdmin;
       lightAdmin = loginNewAdmin(true, permissions);
       sudo(new ExperimenterI(normalUser.userId, false));
       /* Create a Dataset and Project being sudoed as normalUser.*/
       client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
       Project proj = mmFactory.simpleProject();
       Dataset dat = mmFactory.simpleDataset();
       Project sentProj = null;
       Dataset sentDat = null;
       sentProj = (Project) iUpdate.saveAndReturnObject(proj);
       sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
       /* Import an image for the normalUser into the normalUser's default group
        * and target it into the created Dataset.*/
       List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
       OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
       Image image = (Image) originalFileAndImage.get(1);
       assertOwnedBy(image, normalUser);
       /* Link the Project and the Dataset.*/
       ProjectDatasetLink projectDatasetLink = linkProjectDataset(sentProj, sentDat);
       IObject datasetImageLink = iQuery.findByQuery(
               "FROM DatasetImageLink WHERE child.id = :id",
               new ParametersI().addId(image.getId()));
       /* Take care of post-import workflows which do not use sudo.*/
       if (!isSudoing) {
           loginUser(lightAdmin);
           client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
       }
       /* Check that lightAdmin can delete the objects
        * created on behalf of normalUser only if lightAdmin has sufficient permissions.
        * Note that deletion of the Project
        * would delete the whole hierarchy, which was successfully tested
        * during writing of this test. The order of the below delete() commands
        * is intentional, as the ability to delete the links and P/D/I separately is
        * tested in this way.
        * Also check that the canDelete boolean
        * on the object retrieved by the lightAdmin matches the deletePassing
        * boolean.*/
       Assert.assertEquals(getCurrentPermissions(datasetImageLink).canDelete(), deletePassing);
       doChange(client, factory, Requests.delete().target(datasetImageLink).build(), deletePassing);
       Assert.assertEquals(getCurrentPermissions(projectDatasetLink).canDelete(), deletePassing);
       doChange(client, factory, Requests.delete().target(projectDatasetLink).build(), deletePassing);
       Assert.assertEquals(getCurrentPermissions(image).canDelete(), deletePassing);
       doChange(client, factory, Requests.delete().target(image).build(), deletePassing);
       Assert.assertEquals(getCurrentPermissions(sentDat).canDelete(), deletePassing);
       doChange(client, factory, Requests.delete().target(sentDat).build(), deletePassing);
       Assert.assertEquals(getCurrentPermissions(sentProj).canDelete(), deletePassing);
       doChange(client, factory, Requests.delete().target(sentProj).build(), deletePassing);

       /* Check the existence/non-existence of the objects as appropriate.*/
       if (deletePassing) {
           assertDoesNotExist(originalFile);
           assertDoesNotExist(image);
           assertDoesNotExist(sentDat);
           assertDoesNotExist(sentProj);
           assertDoesNotExist(datasetImageLink);
           assertDoesNotExist(projectDatasetLink);
       } else {
           assertExists(originalFile);
           assertExists(image);
           assertExists(sentDat);
           assertExists(sentProj);
           assertExists(datasetImageLink);
           assertExists(projectDatasetLink);
       }
   }

    /**
     * Test that a light admin can edit the name of a project
     * on behalf of another user either using <tt>Sudo</tt> privilege
     * or not using it, but having <tt>WriteOwned</tt> privilege instead.
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/18u5hRD_oFKjGlGQ7x55oUowrmdjS4HXkXKfJSlpbiVY/edit#slide=id.p7</a>
     */
    @Test(dataProvider = "isSudoing and WriteOwned privileges cases")
    public void testEdit(boolean isSudoing, boolean permWriteOwned,
            String groupPermissions) throws Exception {
        final boolean isExpectSuccess = isSudoing || permWriteOwned;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        permissions.add(AdminPrivilegeSudo.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* Create Project as normalUser.*/
        loginUser(normalUser);
        Project proj = mmFactory.simpleProject();
        final String originalName = "OriginalNameOfNormalUser";
        proj.setName(omero.rtypes.rstring(originalName));
        Project sentProj = (Project) iUpdate.saveAndReturnObject(proj);
        String savedOriginalName = sentProj.getName().getValue().toString();
        loginUser(lightAdmin);
        /* As lightAdmin, sudo as the normalUser if this should be the case */
        if (isSudoing) sudo(new ExperimenterI(normalUser.userId, false));
        /* Check that the canEdit flag on the created project is as expected */
        Assert.assertEquals(getCurrentPermissions(sentProj).canEdit(), isExpectSuccess);
        /* Try to rename the Project.*/
        final String changedName = "ChangedNameOfLightAdmin";
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        long id = sentProj.getId().getValue();
        final Project retrievedUnrenamedProject = (Project) iQuery.get("Project", id);
        retrievedUnrenamedProject.setName(omero.rtypes.rstring(changedName));
        /* Check that lightAdmin can edit the Project of normalUser only when
         * lightAdmin is equipped with sufficient permissions, captured in boolean isExpectSuccess.*/
        try {
            sentProj = (Project) iUpdate.saveAndReturnObject(retrievedUnrenamedProject);
            Assert.assertTrue(isExpectSuccess);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccess);
        }
        String savedChangedName = sentProj.getName().getValue().toString();
        logRootIntoGroup(normalUser.groupId);
        final String retrievedName = ((RString) iQuery.projection(
                "SELECT name FROM Project p WHERE p.id = :id",
                new ParametersI().addId(sentProj.getId())).get(0).get(0)).getValue();
        /* Check that the Project still belongs to normalUser and the name of the Project
         * was changed and saved or original name is retained as appropriate */
        assertOwnedBy(sentProj, normalUser);
        if (isExpectSuccess) {
            Assert.assertEquals(savedChangedName, retrievedName);
            Assert.assertEquals(savedChangedName, changedName);
        } else {
            Assert.assertEquals(savedOriginalName, retrievedName);
            Assert.assertEquals(savedOriginalName, originalName);
        }
    }

    /**
     * Test that light admin can
     * chgrp on behalf of another user in cases where the user (owner of the data)
     * is a member of both groups. The chgrp action succeeds if <tt>Sudo</tt> privilege
     * is used. If lightAdmin does not sudo, then the <tt>Chgrp</tt> privilege is necessary.
     * Also tests the ability of the <tt>Chgrp</tt> privilege and chgrp command
     * to sever necessary links for performing the chgrp. This is achieved by
     * having the image which is getting moved into a different group in a dataset
     * in the original group (the chgrp has to sever the DatasetImageLink to perform
     * the move (chgrp)). <tt>Chgrp</tt> privilege is sufficient also
     * to move annotations on the moved objects (tag and file attachment are tested here).
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permChgrp if to test a user who has the <tt>Chgrp</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1BUSzGp89en0Y7-5i7DkQbtVM-AgyfCJVa7rLhnMeO5A/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "isSudoing and Chgrp privileges cases")
    public void testChgrp(boolean isSudoing, boolean permChgrp, String groupPermissions)
            throws Exception {
        /* Set up a user and three groups, the user being a member of
         * two of the groups.*/
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Group where the user is a member.*/
        final long normalUsersOtherGroupId = newGroupAddUser(groupPermissions, normalUser.userId, false).getId().getValue();
        /* If normalUser (data owner) is member of target group,
         * Chgrp action passes when lightAdmin is
         * Sudoed as the normalUser (data owner) or when Chgrp permission is given to lightAdmin.
         * A successful chgrp action will also move all annotations on the moved image,
         * which are unique on the image.*/
        boolean isExpectSuccessInMemberGroup = permChgrp || isSudoing;
        /* create a Dataset as normalUser and import into it */
        loginUser(normalUser);
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
        List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
        OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
        Image image = (Image) originalFileAndImage.get(1);

        /* Annotate the imported image with Tag and file attachment */
        List<IObject> annotOriginalFileAnnotationTagAndLinks = annotateImageWithTagAndFile(image);

        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        permissions.add(AdminPrivilegeSudo.value);
        if (permChgrp) permissions.add(AdminPrivilegeChgrp.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        sudo(new ExperimenterI(normalUser.userId, false));

        /* Take care of workflows which do not use sudo.*/
        if (!isSudoing) {
            loginUser(lightAdmin);
        }
        /* In order to find the image in whatever group, get to all groups context.*/
        mergeIntoContext(client.getImplicitContext(), ALL_GROUPS_CONTEXT);
        /* lightAdmin tries to move the image into another group of the normalUser
         * which should succeed if sudoing and also in case
         * the light admin has Chgrp permissions
         * (i.e. isExpectSuccessInMemberGroup is true). Also check that
         * the canChgrp boolean matches the isExpectSuccessInMemberGroup boolean value */
        Assert.assertEquals(getCurrentPermissions(image).canChgrp(), isExpectSuccessInMemberGroup);
        doChange(client, factory, Requests.chgrp().target(image).toGroup(normalUsersOtherGroupId).build(), isExpectSuccessInMemberGroup);
        if (isExpectSuccessInMemberGroup) {
            assertInGroup(image, normalUsersOtherGroupId);
            assertInGroup(originalFile, normalUsersOtherGroupId);
            /* Annotations on the image changed the group with the image.*/
            assertInGroup(annotOriginalFileAnnotationTagAndLinks, normalUsersOtherGroupId);
        } else {
            assertInGroup(image, normalUser.groupId);
            assertInGroup(originalFile, normalUser.groupId);
            /* The annotations were not moved.*/
            assertInGroup(annotOriginalFileAnnotationTagAndLinks, normalUser.groupId);
        }
        /* In any case, the image should still belong to normalUser.*/
        assertOwnedBy(image, normalUser);
    }

    /**
     * Tests that light admin can, having the <tt>Chgrp</tt>
     * privilege move another user's data into another group where the
     * owner of the data is not member.
     * <tt>Sudo</tt> privilege and being sudoed should not be sufficient,
     * and also, when being sudoed, the cancellation of the <tt>Chgrp</tt> privilege
     * by being sudoed as normalUser causes that the move of the image fails.
     * Also tests the ability of the <tt>Chgrp</tt> privilege and chgrp command
     * to sever necessary links for performing the chgrp. This is achieved by
     * having the image which is getting moved into a different group in a dataset
     * in the original group (the chgrp has to sever the DatasetImageLink to perform
     * the move (chgrp). <tt>Chgrp</tt> privilege is sufficient also
     * to move annotations (tag and file attachment are tested here).
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permChgrp if to test a user who has the <tt>Chgrp</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1c3TGh4bD5_djKO_-lJs5LoBjL0koYsko7QkyEuu-nXY/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "isSudoing and Chgrp privileges cases")
    public void testChgrpNonMember(boolean isSudoing, boolean permChgrp, String groupPermissions)
            throws Exception {
        /* Set up a user (normalUser) and two groups, the normalUser being a member of
         * only one of the groups.*/
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* group where the normalUser is not member */
        final long anotherGroupId = newUserAndGroup(groupPermissions).groupId;
        /* When normalUser (data owner) is not member of the target group,
         * Chgrp action passes only when lightAdmin has Chgrp permission
         * and lightAdmin is not Sudoing. This permission situation should be also valid
         * for all annotations on the image which are unique on the image (not used
         * anywhere else).*/
        boolean chgrpNonMemberExpectSuccess = !isSudoing && permChgrp;
        /* Define cases where canChgrp on the image is expected to be true.
         * As the canChgrp boolean cannot "know" in advance to which group the
         * move is intended, it must show "true" in every case in which SOME
         * chgrp might be successful.*/
        final boolean canChgrpExpectedTrue = permChgrp || isSudoing;
        /* Create a Dataset as the normalUser and import into it */
        loginUser(normalUser);
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
        List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
        OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
        Image image = (Image) originalFileAndImage.get(1);

        /* Annotate the imported image with Tag and file attachment */
        List<IObject> annotOriginalFileAnnotationTagAndLinks = annotateImageWithTagAndFile(image);

        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        permissions.add(AdminPrivilegeSudo.value);
        if (permChgrp) permissions.add(AdminPrivilegeChgrp.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        sudo(new ExperimenterI(normalUser.userId, false));

        /* Take care of workflows which do not use sudo.*/
        if (!isSudoing) {
            loginUser(lightAdmin);
        }
        /* In order to find the image in whatever group, get all groups context.*/
        mergeIntoContext(client.getImplicitContext(), ALL_GROUPS_CONTEXT);

        /* Try to move the image into anotherGroup the normalUser
         * is not a member of, which should fail in all cases
         * except the lightAdmin has Chgrp permission and is not sudoing
         * (i.e. chgrpNoSudoExpectSuccessAnyGroup is true). Also check the
         * the canChgrp boolean.*/
        Assert.assertEquals(getCurrentPermissions(image).canChgrp(), canChgrpExpectedTrue);
        doChange(client, factory, Requests.chgrp().target(image).toGroup(anotherGroupId).build(),
                chgrpNonMemberExpectSuccess);
        if (chgrpNonMemberExpectSuccess) {
            /* Check that the image and its original file moved to another group.*/
            assertInGroup(image, anotherGroupId);
            assertInGroup(originalFile, anotherGroupId);
            /* check the annotations on the image changed the group as expected */
            assertInGroup(annotOriginalFileAnnotationTagAndLinks, anotherGroupId);
        } else {
            /* Check that the image is still in its original group (normalUser's group).*/
            assertInGroup(image, normalUser.groupId);
            assertInGroup(originalFile, normalUser.groupId);
            /* The annotations stayed with the image in the normalUser's group.*/
            assertInGroup(annotOriginalFileAnnotationTagAndLinks, normalUser.groupId);
        }
        /* In any case, the image should still belong to normalUser.*/
        assertOwnedBy(image, normalUser);
    }

    /**
     * Test that light admin can, having the <tt>Chown</tt> privilege,
     * transfer the data between two users (normalUser and anotherUser).
     * Test also that light admin, if sudoed, cannot transfer ownership,
     * because light admin sudoes as a non-admin non-group owner user.
     * In case of private group the transfer of an Image severs the link between the Dataset and Image.
     * For this unlinking, only the Chown permissions are sufficient, no other permissions are necessary.
     * <tt>Chown</tt> privilege is sufficient also
     * to transfer ownership of annotations (tag and file attachment are tested here),
     * but just in case of private and read-only groups, which is in line with the
     * general behaviour of the <tt>Chown</tt> command.
     * @param isSudoing if to test a success of workflows where Sudoed in
     * @param permChown if to test a user who has the <tt>Chown</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1r4qlG9JKLfTgS8s5xWM8SDJJ25t4RwSWuiy6BfFfO_o/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "isSudoing and Chown privileges cases")
    public void testChown(boolean isSudoing, boolean permChown, String groupPermissions)
            throws Exception {
        /* Define the conditions for the chown of the image is passing.*/
        final boolean chownImagePassing = permChown && !isSudoing;
        /* Chown of the annotations on the image is passing when
         * chownImagePassing is true in higher permissions groups (read-annotate and read-write)
         * only.*/
        final boolean annotationsChownExpectSuccess = chownImagePassing &&
                (groupPermissions.equals("rw----") || groupPermissions.equals("rwr---"));

        final EventContext normalUser = newUserAndGroup(groupPermissions);
        final EventContext anotherUser = newUserAndGroup(groupPermissions);
        /* Create a Dataset as the normalUser and import into it */
        loginUser(normalUser);
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
        List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
        OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
        Image image = (Image) originalFileAndImage.get(1);

        /* Annotate the imported image with Tag and file attachment */
        List<IObject> annotOriginalFileAnnotationTagAndLinks = annotateImageWithTagAndFile(image);

        /* Set up the basic permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        permissions.add(AdminPrivilegeSudo.value);
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        sudo(new ExperimenterI(normalUser.userId, false));

        /* Take care of workflows which do not use sudo.*/
        if (!isSudoing) {
            loginUser(lightAdmin);
        }
        /* Check that the value of canChown boolean matches chownPassingWhenNotSudoing
         * boolean in each case.*/
        Assert.assertEquals(getCurrentPermissions(image).canChown(), chownImagePassing);
        /* Get inot correct group context and check all cases.*/
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        /* lightAdmin tries to chown the image.*/
        doChange(client, factory, Requests.chown().target(image).toUser(anotherUser.userId).build(), chownImagePassing);
        /* Chect the results of the chown when lightAdmin is sudoed,
         * which should fail in any case.*/
        if (isSudoing) {
            assertOwnedBy(image, normalUser);
            assertOwnedBy(originalFile, normalUser);
            assertOwnedBy(annotOriginalFileAnnotationTagAndLinks, normalUser);
        /* Check the chown was successful for both the image and the annotations
         * when the permissions for chowning both
         * the image as well as the annotations on it are sufficient.*/
        } else if (chownImagePassing && annotationsChownExpectSuccess) {
            assertOwnedBy(image, anotherUser);
            assertOwnedBy(originalFile, anotherUser);
            /* Annotations will be chowned because
             * groupPermissions are private or read-only (captured in boolean
             * annotationsChownExpectSuccess) */
            assertOwnedBy(annotOriginalFileAnnotationTagAndLinks, anotherUser);
        /* Check the chown was successful for the image but not the annotations
         * in case the annotationsChownExpectSuccess is false, i.e. in read-only and private group.*/
        } else if (chownImagePassing && !annotationsChownExpectSuccess){
            assertOwnedBy(image, anotherUser);
            assertOwnedBy(originalFile, anotherUser);
            assertOwnedBy(annotOriginalFileAnnotationTagAndLinks, normalUser);
        } else {
        /* In the remaining case, the chown will fail, as the chownPassingWhenNotSudoing
         * is false because permChown was not given. All objects belong to normalUser.*/
            assertOwnedBy(image, normalUser);
            assertOwnedBy(originalFile, normalUser);
            assertOwnedBy(annotOriginalFileAnnotationTagAndLinks, normalUser);
        }
        /* In any case, the image must be in the right group */
        assertInGroup(image, normalUser.groupId);
    }

    /**
     * Test that light admin "importing for others" workflow
     * without using Sudo.
     * lightAdmin imports into group of the normalUser (future owner of the data).
     * lightAdmin then transfers the ownership of the imported data to normalUser.
     * For this test, combinations of <tt>Chown</tt>, <tt>WriteOwned</tt>,
     *  <tt>WriteFile</tt> and <tt>WriteManagedRepo</tt> privileges will be explored
     * for lightAdmin. For this workflow the creation and targeting of a Dataset
     * is tested too.
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param permWriteManagedRepo if to test a user who has the <tt>WriteManagedRepo</tt> privilege
     * @param permWriteFile if to test a user who has the <tt>WriteFile</tt> privilege
     * @param permChown if to test a user who has the <tt>Chown</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1w_W6g69CV5Uy_rUom2K9O86w2Q2CGkl3rzNj-ZQzSt0/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "WriteOwned, WriteFile, WriteManagedRepo and Chown privileges cases")
    public void testImporterAsNoSudoChownOnlyWorkflow(boolean permWriteOwned, boolean permWriteFile,
            boolean permWriteManagedRepo, boolean permChown, String groupPermissions) throws Exception {
        /* Define case in which the import not using sudo and importing into a group
         * the light admin is not a member of is expected to succeed.*/
        boolean importNotYourGroupExpectSuccess = permWriteOwned && permWriteFile && permWriteManagedRepo;
        /* Define case in which the creation of Dataset belonging to lightAdmin
         * in a group where lightAdmin is not member is expected to succeed.*/
        boolean createDatasetExpectSuccess = permWriteOwned;
        /* Define case in which the whole workflow is possible (as lightAdmin create
         * Dataset, import into it, then chown the Dataset with the imported
         * image to normalUser).*/
        boolean createDatasetImportNotYourGroupAndChownExpectSuccess =
                permChown && permWriteManagedRepo && permWriteOwned && permWriteFile;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);
        if (permWriteFile) permissions.add(AdminPrivilegeWriteFile.value);
        if (permWriteManagedRepo) permissions.add(AdminPrivilegeWriteManagedRepo.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* lightAdmin creates Dataset in the normalUser's group
         * (lightAdmin is not member of that group).*/
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = null;
        /* Creation of Dataset success is governed by
         * createDatasetExpectSuccess boolean (defined above).*/
        try {
            sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
            Assert.assertTrue(createDatasetExpectSuccess);
        } catch (ServerError se) {
            Assert.assertFalse(createDatasetExpectSuccess);
        }
        /* As lightAdmin, import an Image into the created Dataset.*/
        OriginalFile originalFile = null;
        Image image = null;
        /* Import success is governed by importNotYourGroupExpectSuccess boolean (defined above).*/
        try {
            List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
            originalFile = (OriginalFile) originalFileAndImage.get(0);
            image = (Image) originalFileAndImage.get(1);
        } catch (ServerError se) {
            Assert.assertFalse(importNotYourGroupExpectSuccess);
        }
        /* Check the ownership and group of the original file and the image.*/
        if (importNotYourGroupExpectSuccess) {
            assertOwnedBy(originalFile, lightAdmin);
            assertInGroup(originalFile, normalUser.groupId);
            assertOwnedBy(image, lightAdmin);
            assertInGroup(image, normalUser.groupId);
        /* In case the import was not successful, Image does not exist.
         * Further testing is not interesting in such case.*/
        } else {
            Assert.assertNull(originalFile, "if import failed, the remoteFile should be null");
            Assert.assertNull(image, "if import failed, the image should be null");
            return;
        }
        /* Check that the canChown value on the Dataset matches the boolean
         * createDatasetImportNotYourGroupAndChownExpectSuccess.*/
        Assert.assertEquals(getCurrentPermissions(sentDat).canChown(),
                createDatasetImportNotYourGroupAndChownExpectSuccess);
        /* lightAdmin tries to change the ownership of the Dataset to normalUser */
        doChange(client, factory, Requests.chown().target(sentDat).toUser(normalUser.userId).build(),
                createDatasetImportNotYourGroupAndChownExpectSuccess);
        final List<RType> resultForLink = iQuery.projection(
                "SELECT id FROM DatasetImageLink WHERE parent.id  = :id",
                new ParametersI().addId(sentDat.getId())).get(0);
        final long linkId = ((RLong) resultForLink.get(0)).getValue();
        /* Check that image, dataset and link are in the normalUser's group
         * and belong to normalUser in case the workflow succeeded.*/
        if (createDatasetImportNotYourGroupAndChownExpectSuccess) {
            assertOwnedBy(image, normalUser);
            assertInGroup(image, normalUser.groupId);
            assertOwnedBy(sentDat, normalUser);
            assertInGroup(sentDat, normalUser.groupId);
            assertOwnedBy((new DatasetImageLinkI(linkId, false)), normalUser);
            assertInGroup((new DatasetImageLinkI(linkId, false)), normalUser.groupId);
            assertOwnedBy(originalFile, normalUser);
            assertInGroup(originalFile, normalUser.groupId);
        /* Check that the image, dataset and link still belongs
         * to the light admin as the chown failed, but are in the group of normalUser.*/
        } else {
            assertOwnedBy(image, lightAdmin);
            assertInGroup(image, normalUser.groupId);
            assertOwnedBy(sentDat, lightAdmin);
            assertInGroup(sentDat, normalUser.groupId);
            assertOwnedBy((new DatasetImageLinkI(linkId, false)), lightAdmin);
            assertInGroup((new DatasetImageLinkI(linkId, false)), normalUser.groupId);
        }
    }

    /** lightAdmin tries to link an object to a pre-existing container (image or Dataset)
     * in the target group (of normalUser where lightAdmin is not member)
     * and linking of the image or dataset to this container
     * (Dataset or Project). The image import (by lightAdmin for others)
     * has been tested in other tests,
     * here the image, Dataset and Project is created by normalUser and saved, then
     * the linking to a container by lightAdmin is tested. Only when lightAdmin has
     * WriteOwned privilege is the linking possible.
     * @param permChown if to test a user who has the <tt>Chown</tt> privilege
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1uetvPv-tsnHdRqkVvMx2xpHvVXYyUL2HTob8LUC6Mds/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "WriteOwned and Chown privileges cases")
    public void testLinkNoSudo(boolean permWriteOwned, boolean permChown,
            String groupPermissions) throws Exception {
        /* WriteOwned permission is necessary and sufficient for lightAdmin to link
         * others objects. Exception is Private group, where such linking will
         * fail in all cases.*/
        boolean isExpectLinkingSuccess = permWriteOwned && !groupPermissions.equals("rw----");
        boolean isExpectSuccessLinkAndChown = isExpectLinkingSuccess && permChown;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* Create an image, Dataset and Project as normalUser in normalUser's group.*/
        loginUser(normalUser);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        Image image = mmFactory.createImage();
        Image sentImage = (Image) iUpdate.saveAndReturnObject(image);
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
        Project proj = mmFactory.simpleProject();
        Project sentProj = (Project) iUpdate.saveAndReturnObject(proj);
        loginUser(lightAdmin);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        /* lightAdmin checks that the canLink value on all the objects to be linked
         * matches the isExpectLinkingSuccess boolean.*/
        Assert.assertEquals(getCurrentPermissions(sentImage).canLink(), isExpectLinkingSuccess);
        Assert.assertEquals(getCurrentPermissions(sentDat).canLink(), isExpectLinkingSuccess);
        Assert.assertEquals(getCurrentPermissions(sentProj).canLink(), isExpectLinkingSuccess);
        /* lightAdmin tries to create links between the image and Dataset
         * and between Dataset and Project.
         * If links could not be created, finish the test */
        DatasetImageLink linkOfDatasetImage = new DatasetImageLinkI();
        ProjectDatasetLink linkOfProjectDataset = new ProjectDatasetLinkI();
        try {
            linkOfDatasetImage = linkDatasetImage(sentDat, sentImage);
            linkOfProjectDataset = linkProjectDataset(sentProj, sentDat);
            Assert.assertTrue(isExpectLinkingSuccess);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectLinkingSuccess);
            return;
        }

        /* Check that the value of canChown boolean on the links is matching
         * the isExpectSuccessLinkAndChown boolean.*/
        Assert.assertEquals(getCurrentPermissions(linkOfDatasetImage).canChown(), isExpectSuccessLinkAndChown);
        Assert.assertEquals(getCurrentPermissions(linkOfProjectDataset).canChown(), isExpectSuccessLinkAndChown);

        /* lightAdmin transfers the ownership of both links to normalUser.
         * The success of the whole linking and chowning
         * operation is captured in boolean isExpectSuccessLinkAndChown. Note that the
         * ownership of the links must be transferred explicitly, as the Chown feature
         * on the Project would not transfer ownership links owned by non-owners
         * of the P/D/I objects (chown on mixed ownership hierarchy does not chown objects
         * owned by other users).*/
        Chown2 chown = Requests.chown().target(linkOfDatasetImage).toUser(normalUser.userId).build();
        doChange(client, factory, chown, isExpectSuccessLinkAndChown);
        chown = Requests.chown().target(linkOfProjectDataset).toUser(normalUser.userId).build();
        doChange(client, factory, chown, isExpectSuccessLinkAndChown);

        /* Check the ownership of the links, image, Dataset and Project.*/
        final long linkDatasetImageId = ((RLong) iQuery.projection(
                "SELECT id FROM DatasetImageLink WHERE parent.id  = :id",
                new ParametersI().addId(sentDat.getId())).get(0).get(0)).getValue();
        final long linkProjectDatasetId = ((RLong) iQuery.projection(
                "SELECT id FROM ProjectDatasetLink WHERE parent.id  = :id",
                new ParametersI().addId(sentProj.getId())).get(0).get(0)).getValue();
        assertOwnedBy(sentImage, normalUser);
        assertOwnedBy(sentDat, normalUser);
        assertOwnedBy(sentProj, normalUser);
        if (isExpectSuccessLinkAndChown) {
            assertOwnedBy((new DatasetImageLinkI(linkDatasetImageId, false)), normalUser);
            assertOwnedBy((new ProjectDatasetLinkI(linkProjectDatasetId, false)), normalUser);
        } else {
            assertOwnedBy((new DatasetImageLinkI(linkDatasetImageId, false)), lightAdmin);
            assertOwnedBy((new ProjectDatasetLinkI(linkProjectDatasetId, false)), lightAdmin);
        }
    }

        /** Light admin (lightAdmin) imports data for others (normalUser) without using Sudo.
         * The data will be imported by lightAdmin to lightAdmin's group (normalUser is not member
         * of lightAdmin's group). lightAdmin moves the data into normalUser's group
         * and chowns the data to normalUser.
         * For this test, combinations of <tt>Chown</tt>, <tt>Chgrp</tt>,
         * privileges is explored for the light admin.
         * For this workflow the creation and targeting of a Dataset
         * is tested too.
         * @param permChgrp if to test a user who has the <tt>Chgrp</tt> privilege
         * @param permChown if to test a user who has the <tt>Chown</tt> privilege
         * @param groupPermissions if to test the effect of group permission level
         * @throws Exception unexpected
         * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1zqDRwYDm3wA_xE79M6qR56U8giFbLFDywH3slj0wURA/edit#slide=id.p4</a>
         */
        @Test(dataProvider = "Chgrp and Chown privileges cases")
        public void testImporterAsNoSudoChgrpChownWorkflow(boolean permChgrp, boolean permChown,
                String groupPermissions) throws Exception {
        /* Importing into the group of the lightAdmin and
         * subsequent moving the data into the group of normalUser and chowning
         * them to the normalUser succeeds if Chgrp and Chown is possible,
         * which needs permChgrp, permChown, but not WriteFile and WriteOwned,*/
        boolean importYourGroupAndChgrpAndChownExpectSuccess = permChgrp && permChown;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        if (permChgrp) permissions.add(AdminPrivilegeChgrp.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* lightAdmin creates a Dataset in lightAdmin's group and imports
         * an image into it.*/
        client.getImplicitContext().put("omero.group", Long.toString(lightAdmin.groupId));
        Dataset dat = mmFactory.simpleDataset();
        Dataset sentDat = (Dataset) iUpdate.saveAndReturnObject(dat);
        /* Import an image into the created Dataset.*/
        List<IObject> originalFileAndImage = importImageWithOriginalFile(sentDat);
        OriginalFile originalFile = (OriginalFile) originalFileAndImage.get(0);
        Image image = (Image) originalFileAndImage.get(1);
        /* Check that originalFile and the image
         * corresponding to the originalFile are in the right group.*/
        assertOwnedBy(originalFile, lightAdmin);
        assertInGroup(originalFile, lightAdmin.groupId);
        assertOwnedBy(image, lightAdmin);
        assertInGroup(image, lightAdmin.groupId);

        /* In order to find the image in whatever group, get all groups context.*/
        mergeIntoContext(client.getImplicitContext(), ALL_GROUPS_CONTEXT);
        /* Check that the value of canChgrp on the dataset is true.
         * Note that although the move into normalUser's group might fail,
         * lightAdmin could be moving the dataset into some group where they are member,
         * and thus the canChgrp must be "true".*/
        Assert.assertTrue(getCurrentPermissions(sentDat).canChgrp());
        /* lightAdmin tries to move the dataset (and with it the linked image)
         * from lightAdmin's group to normalUser's group,
         * which should succeed in case the light admin has Chgrp permissions.*/
        doChange(client, factory, Requests.chgrp().target(sentDat).toGroup(normalUser.groupId).build(), permChgrp);
        /* Check that the group of the moved objects.*/
        long datasetImageLinkId = ((RLong) iQuery.projection(
                "SELECT id FROM DatasetImageLink WHERE parent.id = :id",
                new ParametersI().addId(sentDat.getId())).get(0).get(0)).getValue();
        if (permChgrp) {
            assertInGroup(originalFile, normalUser.groupId);
            assertInGroup(image, normalUser.groupId);
            assertInGroup(sentDat, normalUser.groupId);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), normalUser.groupId);
        } else {
            assertInGroup(originalFile, lightAdmin.groupId);
            assertInGroup(image, lightAdmin.groupId);
            assertInGroup(sentDat, lightAdmin.groupId);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), lightAdmin.groupId);
        }
        /* Check that the canChown boolean on Dataset is matching permChown boolean.*/
        Assert.assertEquals(getCurrentPermissions(sentDat).canChown(), permChown);
        /* lightAdmin tries to transfer the ownership of Dataset to normalUser.
         * Chowning the Dataset succeeds if lightAdmin has Chown privilege.
         * Successful chowning of the dataset transfers the ownership of the linked image
         * and the link too.*/
        doChange(client, factory, Requests.chown().target(sentDat).toUser(normalUser.userId).build(), permChown);
        /* Boolean importYourGroupAndChgrpAndChownExpectSuccess
         * captures permChown and permChgrp. Check the objects ownership and groups.*/
        if (importYourGroupAndChgrpAndChownExpectSuccess) {
            /* First case: The whole "import for others" workflow succeeds.
             * Image, Dataset and link are in normalUser's group and belong to normalUser.*/
            assertOwnedBy(originalFile, normalUser);
            assertInGroup(originalFile, normalUser.groupId);
            assertOwnedBy(image, normalUser);
            assertInGroup(image, normalUser.groupId);
            assertOwnedBy(sentDat, normalUser);
            assertInGroup(sentDat, normalUser.groupId);
            assertOwnedBy((new DatasetImageLinkI(datasetImageLinkId, false)), normalUser);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), normalUser.groupId);
        } else if (permChown) {
            /* Second case: Chown succeeds, but Chgrp fails.
             * Image, Dataset and link belong to the normalUser, but are in lightAdmin's group */
            assertOwnedBy(originalFile, normalUser);
            assertInGroup(originalFile, lightAdmin.groupId);
            assertOwnedBy(image, normalUser);
            assertInGroup(image, lightAdmin.groupId);
            assertOwnedBy(sentDat, normalUser);
            assertInGroup(sentDat, lightAdmin.groupId);
            assertOwnedBy((new DatasetImageLinkI(datasetImageLinkId, false)), normalUser);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), lightAdmin.groupId);
        } else if (permChgrp) {
            /* Third case: Chgrp succeeds, but Chown fails.
             * Image, Dataset and link are in normalUser's group but belong to lightAdmin.*/
            assertOwnedBy(originalFile, lightAdmin);
            assertInGroup(originalFile, normalUser.groupId);
            assertOwnedBy(image, lightAdmin);
            assertInGroup(image, normalUser.groupId);
            assertOwnedBy(sentDat, lightAdmin);
            assertInGroup(sentDat, normalUser.groupId);
            assertOwnedBy((new DatasetImageLinkI(datasetImageLinkId, false)), lightAdmin);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), normalUser.groupId);
        } else {
            /* Fourth case: Ghgrp and Chown both fail.
             * Image, Dataset and link are in lightAdmin's group and belong to lightAdmin.*/
            assertOwnedBy(originalFile, lightAdmin);
            assertInGroup(originalFile, lightAdmin.groupId);
            assertOwnedBy(image, lightAdmin);
            assertInGroup(image, lightAdmin.groupId);
            assertOwnedBy(sentDat, lightAdmin);
            assertInGroup(sentDat, lightAdmin.groupId);
            assertOwnedBy((new DatasetImageLinkI(datasetImageLinkId, false)), lightAdmin);
            assertInGroup((new DatasetImageLinkI(datasetImageLinkId, false)), lightAdmin.groupId);
        }
    }

    /** Light admin (lightAdmin) transfers the ownership of all the data of a user (normalUser)
     * to another user. The data are in 2 groups, of which the original data owner (normalUser)
     * is member, the recipient of the data is member of just one of the groups. Chown privilege
     * is sufficient for lightAdmin to perform the workflow.
     * @param isPrivileged if to test a user who has the <tt>Chown</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1zbIu5gYPObbVkBSxdD4sMmPFMGgspbtYQEnJ9k8vfCs/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testChownAllBelongingToUser(boolean isPrivileged, String groupPermissions) throws Exception {
        /* Chown privilege is sufficient for the workflow.*/
        final boolean chownPassing = isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        ExperimenterGroup anotherGroup = newGroupAddUser(groupPermissions, normalUser.userId, false);
        final EventContext recipient = newUserInGroup(anotherGroup, false);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeChown.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* normalUser creates two sets of P/D/I hierarchy in their default group.*/
        loginUser(normalUser);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        Image image1 = mmFactory.createImage();
        Image image2 = mmFactory.createImage();
        Image sentImage1 = (Image) iUpdate.saveAndReturnObject(image1);
        Image sentImage2 = (Image) iUpdate.saveAndReturnObject(image2);
        Dataset dat1 = mmFactory.simpleDataset();
        Dataset dat2 = mmFactory.simpleDataset();
        Dataset sentDat1 = (Dataset) iUpdate.saveAndReturnObject(dat1);
        Dataset sentDat2 = (Dataset) iUpdate.saveAndReturnObject(dat2);
        Project proj1 = mmFactory.simpleProject();
        Project proj2 = mmFactory.simpleProject();
        Project sentProj1 = (Project) iUpdate.saveAndReturnObject(proj1);
        Project sentProj2 = (Project) iUpdate.saveAndReturnObject(proj2);
        DatasetImageLink linkOfDatasetImage1 = linkDatasetImage(sentDat1, sentImage1);
        DatasetImageLink linkOfDatasetImage2 = linkDatasetImage(sentDat2, sentImage2);
        ProjectDatasetLink linkOfProjectDataset1 = linkProjectDataset(sentProj1, sentDat1);
        ProjectDatasetLink linkOfProjectDataset2 = linkProjectDataset(sentProj2, sentDat2);

        /* normalUser creates two sets of P/D?I hierarchy in the other group (anotherGroup).*/
        client.getImplicitContext().put("omero.group", Long.toString(anotherGroup.getId().getValue()));
        Image image1AnotherGroup = mmFactory.createImage();
        Image image2AnotherGroup = mmFactory.createImage();
        Image sentImage1AnootherGroup = (Image) iUpdate.saveAndReturnObject(image1AnotherGroup);
        Image sentImage2AnotherGroup = (Image) iUpdate.saveAndReturnObject(image2AnotherGroup);
        Dataset dat1AnotherGroup = mmFactory.simpleDataset();
        Dataset dat2AnotherGroup = mmFactory.simpleDataset();
        Dataset sentDat1AnotherGroup = (Dataset) iUpdate.saveAndReturnObject(dat1AnotherGroup);
        Dataset sentDat2AnotherGroup = (Dataset) iUpdate.saveAndReturnObject(dat2AnotherGroup);
        Project proj1AnotherGroup = mmFactory.simpleProject();
        Project proj2AnotherGroup = mmFactory.simpleProject();
        Project sentProj1AnootherGroup = (Project) iUpdate.saveAndReturnObject(proj1AnotherGroup);
        Project sentProj2AnotherGroup = (Project) iUpdate.saveAndReturnObject(proj2AnotherGroup);
        DatasetImageLink linkOfDatasetImage1AnotherGroup = linkDatasetImage(sentDat1AnotherGroup, sentImage1AnootherGroup);
        DatasetImageLink linkOfDatasetImage2AnotherGroup = linkDatasetImage(sentDat2AnotherGroup, sentImage2AnotherGroup);
        ProjectDatasetLink linkOfProjectDataset1AnotherGroup = linkProjectDataset(sentProj1AnootherGroup, sentDat1AnotherGroup);
        ProjectDatasetLink linkOfProjectDataset2AnotherGroup = linkProjectDataset(sentProj2AnotherGroup, sentDat2AnotherGroup);
        /* lightAdmin tries to transfers all normalUser's data to recipient.*/
        loginUser(lightAdmin);
        /* In order to be able to operate in both groups, get all groups context.*/
        mergeIntoContext(client.getImplicitContext(), ALL_GROUPS_CONTEXT);
        /* Check on one selected object only (sentProj1AnotherGroup) the value
         * of canChown. The value must match the chownPassing boolean.*/
        Assert.assertEquals(getCurrentPermissions(sentProj1AnootherGroup).canChown(), chownPassing);
        /* Check that transfer proceeds only if chownPassing boolean is true.*/
        doChange(client, factory, Requests.chown().targetUsers(normalUser.userId).toUser(recipient.userId).build(), chownPassing);
        if (!chownPassing) {
            /* Finish the test if no transfer of data could proceed.*/
            return;
        }
        /* Check the transfer of all the data in normalUser's group was successful,
         * first checking ownership of the first hierarchy set.*/
        assertOwnedBy(sentProj1, recipient);
        assertOwnedBy(sentDat1, recipient);
        assertOwnedBy(sentImage1, recipient);
        assertOwnedBy(linkOfDatasetImage1, recipient);
        assertOwnedBy(linkOfProjectDataset1, recipient);
        /* Check ownership of the second hierarchy set.*/
        assertOwnedBy(sentProj2, recipient);
        assertOwnedBy(sentDat2, recipient);
        assertOwnedBy(sentImage2, recipient);
        assertOwnedBy(linkOfDatasetImage2, recipient);
        assertOwnedBy(linkOfProjectDataset2, recipient);
        /* Check ownership of the objects in anotherGroup,
         * first checking ownership of the first hierarchy.*/
        assertOwnedBy(sentProj1AnootherGroup, recipient);
        assertOwnedBy(sentDat1AnotherGroup, recipient);
        assertOwnedBy(sentImage1AnootherGroup, recipient);
        assertOwnedBy(linkOfDatasetImage1AnotherGroup, recipient);
        assertOwnedBy(linkOfProjectDataset1AnotherGroup, recipient);
        /* Check ownership of the second hierarchy set in anotherGroup.*/
        assertOwnedBy(sentProj2AnotherGroup, recipient);
        assertOwnedBy(sentDat2AnotherGroup, recipient);
        assertOwnedBy(sentImage1AnootherGroup, recipient);
        assertOwnedBy(linkOfDatasetImage2AnotherGroup, recipient);
        assertOwnedBy(linkOfProjectDataset2AnotherGroup, recipient);
    }

    /** Light admin (lightAdmin) puts ROI and Rendering Settings on an
     * image of normalUser and then transfers the ownership of the ROI and settings
     * to normalUser, all without using Sudo.
     * @param permChown if to test a user who has the <tt>Chown</tt> privilege
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1ukEZmh0c6NCNKUE1dFqaYjN6Sd5xxCuOYkSuMdpiqvE/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "WriteOwned and Chown privileges cases")
    public void testROIAndRenderingSettingsNoSudo(boolean permWriteOwned, boolean permChown,
            String groupPermissions) throws Exception {
        /* Creation of rendering settings on others' images is permitted with WriteOwned permissions
         * in all group types except private.*/
        boolean isExpectSuccessCreateROIRndSettings = permWriteOwned && !groupPermissions.equals("rw----");
        /* The only necessary additional permission for the whole workflow (creation & chown)
         * to succeed is permChown.*/
        boolean isExpectSuccessCreateAndChown = isExpectSuccessCreateROIRndSettings && permChown;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);

        /* normalUser creates an image with pixels in normalUser's group.*/
        loginUser(normalUser);
        Image image = mmFactory.createImage();
        Image sentImage = (Image) iUpdate.saveAndReturnObject(image);
        Pixels pixelsOfImage = sentImage.getPrimaryPixels();

        /* lightAdmin logs in.*/
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));

        /* lightAdmin tries to set ROI on normalUser's image.*/
        Roi roi = new RoiI();
        roi.addShape(new RectangleI());
        roi.setImage((Image) sentImage.proxy());
        try {
            roi = (Roi) iUpdate.saveAndReturnObject(roi);
            /* Check the value of canAnnotate on the sentImage.
             * The value must be true as the ROI can be saved.*/
            Assert.assertTrue(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertTrue(isExpectSuccessCreateROIRndSettings);
        } catch (SecurityViolation sv) {
            /* Check the value of canAnnotate on the sentImage.
             * The value must be false as the ROI cannot be saved.*/
            Assert.assertFalse(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertFalse(isExpectSuccessCreateROIRndSettings);
        }

        /* lightAdmin tries to set rendering settings on normalUser's image
         * using setOriginalSettingsInSet method */
        IRenderingSettingsPrx prx = factory.getRenderingSettingsService();
        try {
            prx.setOriginalSettingsInSet(Pixels.class.getName(),
                    Arrays.asList(pixelsOfImage.getId().getValue()));
            /* Check the value of canAnnotate on the sentImage.
             * The value must be true as the Rnd settings can be saved.*/
            Assert.assertTrue(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertTrue(isExpectSuccessCreateROIRndSettings);
        } catch (SecurityViolation sv) {
            /* Check the value of canAnnotate on the sentImage.
             * The value must be false as the Rnd settings cannot be saved.*/
            Assert.assertFalse(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertFalse(isExpectSuccessCreateROIRndSettings);
        }
        /* Retrieve the image corresponding to the roi and Rnd settings
         * (if they could be set) and check the roi and rendering settings
         * belong to lightAdmin, whereas the image belongs to normalUser.*/
        RenderingDef rDef = (RenderingDef) iQuery.findByQuery("FROM RenderingDef WHERE pixels.id = :id",
                new ParametersI().addId(pixelsOfImage.getId()));
        if (isExpectSuccessCreateROIRndSettings) {
            long imageId = ((RLong) iQuery.projection(
                    "SELECT rdef.pixels.image.id FROM RenderingDef rdef WHERE rdef.id = :id",
                    new ParametersI().addId(rDef.getId())).get(0).get(0)).getValue();
            assertOwnedBy(roi, lightAdmin);
            assertOwnedBy(rDef, lightAdmin);
            assertOwnedBy((new ImageI(imageId, false)), normalUser);
        } else {
            /* ROI and Rnd settings (rDef) must be null as they could not be set.*/
            roi = (Roi) iQuery.findByQuery("FROM Roi WHERE image.id = :id",
                    new ParametersI().addId(sentImage.getId()));
            Assert.assertNull(roi);
            Assert.assertNull(rDef);
        }
        /* lightAdmin tries to chown the ROI and the rendering settings (rDef) to normalUser.
         * Only attempt the canChown check and the chown if the ROI and rendering settings exist */
        if (isExpectSuccessCreateROIRndSettings) {
            /* Check the value of canChown on the ROI and rendering settings (rDef) matches
             * the boolean isExpectSuccessCreateAndChownRndSettings.*/
            Assert.assertEquals(getCurrentPermissions(roi).canChown(), isExpectSuccessCreateAndChown);
            Assert.assertEquals(getCurrentPermissions(rDef).canChown(), isExpectSuccessCreateAndChown);
            /* Note that in read-only group, the chown of roi would fail, see
             * https://trello.com/c/7o4q2Tkt/745-fix-graphs-for-mixed-ownership-read-only.
             * The workaround used here is to chown both the image and the ROI.*/
            doChange(client, factory, Requests.chown().target(roi, sentImage).toUser(normalUser.userId).build(), isExpectSuccessCreateAndChown);
            doChange(client, factory, Requests.chown().target(rDef).toUser(normalUser.userId).build(), isExpectSuccessCreateAndChown);
            /* Retrieve the image corresponding to the roi and Rnd settings.*/
            long imageId = ((RLong) iQuery.projection(
                    "SELECT rdef.pixels.image.id FROM RenderingDef rdef WHERE rdef.id = :id",
                    new ParametersI().addId(rDef.getId())).get(0).get(0)).getValue();
            if (isExpectSuccessCreateAndChown) {
                /* First case: Workflow succeeded for creation and chown, all belongs to normalUser */
                assertOwnedBy(roi, normalUser);
                assertOwnedBy(rDef, normalUser);
                assertOwnedBy((new ImageI (imageId, false)), normalUser);
            } else {
                /* Second case: Creation succeeded, but the chown failed.*/
                assertOwnedBy(roi, lightAdmin);
                assertOwnedBy(rDef, lightAdmin);
                assertOwnedBy((new ImageI(imageId, false)), normalUser);
            }
        } else {
            /* Third case: Creation did not succeed, and chown was not attempted.*/
            Assert.assertNull(roi);
            Assert.assertNull(rDef);
        }
    }

    /** Light admin (lightAdmin) uploads a File Attachment (fileAnnotation)
     * with original file (originalFile) in a group they are not member of (normalUser's group)
     * and tries to link it to an image of the user (normalUser) and then tries to transfer
     * the ownership of the fileAnnotation and link to normalUser.
     * @param permChown if to test a user who has the <tt>Chown</tt> privilege
     * @param permWriteOwned if to test a user who has the <tt>WriteOwned</tt> privilege
     * @param permWriteFile if to test a user who has the <tt>WriteFile</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     * @see <a href="graphical explanation">https://docs.google.com/presentation/d/1jfdsPSvqJvBlN18vIFrRsNqh13eAlF5Gjh3G_MiGGAE/edit#slide=id.p4</a>
     */
    @Test(dataProvider = "fileAttachment privileges cases")
    public void testFileAttachmentNoSudo(boolean permChown, boolean permWriteOwned,
            boolean permWriteFile, String groupPermissions) throws Exception {
        /* Upload or creation of fileAttachment in not-your-group is permitted for lightAdmin
         * with WriteOwned and WriteFile permissions.*/
        boolean isExpectSuccessCreateFileAttachment = permWriteOwned && permWriteFile;
        /* Linking of fileAttachment to others' image is permitted when the creation
         * in not-your-group is permitted in all group types except private.*/
        boolean isExpectSuccessLinkFileAttachemnt = isExpectSuccessCreateFileAttachment && !(groupPermissions == "rw----");
        /* Chown permission is needed for lightAdmin for successful transfer of ownership of the
         * fileAttachment to normalUser.*/
        boolean isExpectSuccessCreateFileAttAndChown = isExpectSuccessCreateFileAttachment && permChown;
        boolean isExpectSuccessCreateLinkAndChown = isExpectSuccessLinkFileAttachemnt && permChown;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (permChown) permissions.add(AdminPrivilegeChown.value);
        if (permWriteOwned) permissions.add(AdminPrivilegeWriteOwned.value);
        if (permWriteFile) permissions.add(AdminPrivilegeWriteFile.value);

        /* normalUser creates an image with pixels in normalUser's group.*/
        loginUser(normalUser);
        Image image = mmFactory.createImage();
        Image sentImage = (Image) iUpdate.saveAndReturnObject(image);
        /* Login as lightAdmin.*/
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        /* lightAdmin tries to create a fileAttachment in normalUser's group.*/
        FileAnnotation fileAnnotation = mmFactory.createFileAnnotation();
        OriginalFile originalFile = new OriginalFileI();
        try {
            fileAnnotation = (FileAnnotation) iUpdate.saveAndReturnObject(fileAnnotation);
            originalFile = (OriginalFile) fileAnnotation.getFile();
            Assert.assertTrue(isExpectSuccessCreateFileAttachment);
        } catch (SecurityViolation sv) {
            Assert.assertFalse(isExpectSuccessCreateFileAttachment);
            /* Finish the test in case fileAttachment could not be created.*/
            return;
        }
        /* Check that the value of canChown on the fileAnnotation is matching the boolean
         * isExpectSuccessCreateFileAttAndChown.*/
        Assert.assertEquals(getCurrentPermissions(fileAnnotation).canChown(), isExpectSuccessCreateFileAttAndChown);
        /* lightAdmin tries to link the fileAnnotation to the normalUser's image.
         * This will not work in private group. See definition of the boolean
         * isExpectSuccessLinkFileAttachemnt.*/
        ImageAnnotationLink link = new ImageAnnotationLinkI();
        try {
            link = (ImageAnnotationLink) linkImageAnnotation(sentImage, fileAnnotation);
            /* Check the value of canAnnotate on the image is true in successful linking case.*/
            Assert.assertTrue(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertTrue(isExpectSuccessLinkFileAttachemnt);
        } catch (SecurityViolation sv) {
            /* Check the value of canAnnotate on the image is false in case linking fails.*/
            Assert.assertFalse(getCurrentPermissions(sentImage).canAnnotate());
            Assert.assertFalse(isExpectSuccessLinkFileAttachemnt);
            /* Finish the test in case no link could be created.*/
            return;
        }
        /* lightAdmin tries to transfer the ownership of fileAnnotation to normalUser.
         * The test was terminated (see above) in all cases
         * in which the fileAnnotation was not created.*/
        doChange(client, factory, Requests.chown().target(fileAnnotation).toUser(normalUser.userId).build(), isExpectSuccessCreateFileAttAndChown);
        if (isExpectSuccessCreateFileAttAndChown) {
            /* First case: fileAnnotation creation and chowning succeeded.*/
            assertOwnedBy(fileAnnotation, normalUser);
            assertOwnedBy(originalFile, normalUser);
        } else {
            /* Second case: creation of fileAnnotation succeeded, but the chown failed.*/
            assertOwnedBy(fileAnnotation, lightAdmin);
            assertOwnedBy(originalFile, lightAdmin);
        }
        /* Check the value of canChown on the link is matching the boolean
         * isExpectSuccessCreateLinkAndChown.*/
        Assert.assertEquals(getCurrentPermissions(link).canChown(), isExpectSuccessCreateLinkAndChown);
        /* lightAdmin tries to transfer the ownership of link to normalUser.
         * The test was terminated (see above) in all cases
         * in which the link was not created.*/
        doChange(client, factory, Requests.chown().target(link).toUser(normalUser.userId).build(), isExpectSuccessCreateLinkAndChown);
        if (isExpectSuccessCreateLinkAndChown) {
            /* First case: link was created and chowned, the whole workflow succeeded.*/
            link = (ImageAnnotationLink) iQuery.findByQuery("FROM ImageAnnotationLink l JOIN FETCH"
                    + " l.child JOIN FETCH l.parent WHERE l.child.id = :id",
                    new ParametersI().addId(fileAnnotation.getId()));
            assertOwnedBy(link, normalUser);
            assertOwnedBy(fileAnnotation, normalUser);
            assertOwnedBy(originalFile, normalUser);
        } else {
            /* Second case: link was created but could not be chowned.*/
            link = (ImageAnnotationLink) iQuery.findByQuery("FROM ImageAnnotationLink l JOIN FETCH"
                    + " l.child JOIN FETCH l.parent WHERE l.child.id = :id",
                    new ParametersI().addId(fileAnnotation.getId()));
            assertOwnedBy(link, lightAdmin);
        }
    }

    /** Light admin (lightAdming) tries to upload an official script.
     * The only permission lightAdmin needs for this is WriteScriptRepo.
     * @param isPrivileged if to test a user who has the <tt>WriteScriptRepo</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testOfficialSciptUploadNoSudo(boolean isPrivileged, String groupPermissions) throws Exception {
        /* isPrivileged translates in this test into WriteScriptRepo permission, see below.*/
        boolean isExpectSuccessUploadOfficialScript = isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* Set up the light admin's permissions for this test.*/
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeWriteScriptRepo.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        IScriptPrx iScript = factory.getScriptService();
        /* lightAdmin fetches a script from the server.*/
        OriginalFile scriptFile = iScript.getScriptsByMimetype(ScriptServiceTest.PYTHON_MIMETYPE).get(0);
        RawFileStorePrx rfs = factory.createRawFileStore();
        rfs.setFileId(scriptFile.getId().getValue());
        final String actualScript = new String(rfs.read(0, (int) rfs.size()), StandardCharsets.UTF_8);
        rfs.close();
        /* lightAdmin tries uploading the script as a new script in normalUser's group.*/
        iScript = factory.getScriptService();
        final String testScriptName = "Test_" + getClass().getName() + '_' + UUID.randomUUID() + ".py";
        long testScriptId = -1;
        try {
            testScriptId = iScript.uploadOfficialScript(testScriptName, actualScript);
            Assert.assertTrue(isExpectSuccessUploadOfficialScript);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessUploadOfficialScript);
            /* Upload failed so finish the test.*/
            return;
        }
        /* Check that the new script exists in the "user" group.*/
        loginUser(normalUser);
        scriptFile = (OriginalFile) iQuery.get("OriginalFile", testScriptId);
        Assert.assertEquals(scriptFile.getDetails().getOwner().getId().getValue(), roles.rootId);
        Assert.assertEquals(scriptFile.getDetails().getGroup().getId().getValue(), roles.userGroupId);
        /* Check if the script is correctly uploaded.*/
        rfs = factory.createRawFileStore();
        rfs.setFileId(testScriptId);
        final String currentScript = new String(rfs.read(0, (int) rfs.size()), StandardCharsets.UTF_8);
        rfs.close();
        Assert.assertEquals(currentScript, actualScript);
    }

    /**
     * Light admin (lightAdmin) tries to delete official script. They will succeed only if they
     * have the <tt>DeleteScriptRepo</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>DeleteScriptRepo</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testOfficialScriptDeleteNoSudo(boolean isPrivileged, String groupPermissions) throws Exception {
        boolean isExpectSuccessDeleteOfficialScript = isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeDeleteScriptRepo.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        IScriptPrx iScript = factory.getScriptService();
        /* lightAdmin fetches a script from the server.*/
        final OriginalFile scriptFile = iScript.getScriptsByMimetype(ScriptServiceTest.PYTHON_MIMETYPE).get(0);
        RawFileStorePrx rfs = factory.createRawFileStore();
        rfs.setFileId(scriptFile.getId().getValue());
        final String actualScript = new String(rfs.read(0, (int) rfs.size()), StandardCharsets.UTF_8);
        rfs.close();
        /* Another light admin (anotherLightAdmin) with appropriate permissions
         * uploads the script as a new script.*/
        final EventContext anotherLightAdmin = loginNewAdmin(true, AdminPrivilegeWriteScriptRepo.value);
        iScript = factory.getScriptService();
        final String testScriptName = "Test_" + getClass().getName() + '_' + UUID.randomUUID() + ".py";
        final long testScriptId = iScript.uploadOfficialScript(testScriptName, actualScript);
        /* Delete any jobs associated with the script.*/
        final Delete2Builder delete = Requests.delete().option(Requests.option().excludeType("OriginalFile").build());
        for (final IObject scriptJob : iQuery.findAllByQuery(
                "SELECT DISTINCT link.parent FROM JobOriginalFileLink link WHERE link.child.id = :id",
                new ParametersI().addId(testScriptId))) {
            delete.target(scriptJob);
        }
        doChange(delete.build());
        /* Check that the new script exists.*/
        final OriginalFile testScript = new OriginalFileI(testScriptId, false);
        assertExists(testScript);
        /* lightAdmin tries deleting the script.*/
        loginUser(lightAdmin);
        client.getImplicitContext().put("omero.group", Long.toString(normalUser.groupId));
        iScript = factory.getScriptService();
        try {
            iScript.deleteScript(testScriptId);
            Assert.assertTrue(isExpectSuccessDeleteOfficialScript);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessDeleteOfficialScript);
        }
        /* normalUser checks if the script was deleted or left intact.*/
        loginUser(normalUser);
        if (isExpectSuccessDeleteOfficialScript) {
            assertDoesNotExist(testScript);
        } else {
            assertExists(testScript);
        }
        rfs = factory.createRawFileStore();
        try {
            rfs.setFileId(testScriptId);
            final String currentScript = new String(rfs.read(0, (int) rfs.size()), StandardCharsets.UTF_8);
            Assert.assertEquals(currentScript, actualScript);
            Assert.assertFalse(isExpectSuccessDeleteOfficialScript);
        } catch (Ice.LocalException | ServerError se) {
            /* Have to catch both types of exceptions because of
             * RawFileStoreTest.testBadFileId behavior.*/
            Assert.assertTrue(isExpectSuccessDeleteOfficialScript);
        } finally {
            rfs.close();
        }
    }

    /**
     * Test that light admin can modify group membership when he/she has
     * only the <tt>ModifyGroupMembership</tt> privilege.
     * The addition of a user is being attempted here.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroupMembership</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupMembershipAddUser(boolean isPrivileged, String groupPermissions) throws Exception {
        /* the permModifyGroupMembership should be a sufficient permission to perform
         * the user addition into a group */
        boolean isExpectSuccessAddUserToGroup = isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* one extra group is needed to add the existing normalUser to */
        final EventContext otherUser = newUserAndGroup(groupPermissions);
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroupMembership.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter user = new ExperimenterI(normalUser.userId, false);
        final ExperimenterGroup group = new ExperimenterGroupI(otherUser.groupId, false);
        try {
            iAdmin.addGroups(user, Collections.singletonList(group));
            Assert.assertTrue(isExpectSuccessAddUserToGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessAddUserToGroup);
        }
    }

    /**
     * Test that light admin can modify group membership when he/she has
     * only the <tt>ModifyGroupMembership</tt> privilege.
     * The removal of a user is being attempted here.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroupMembership</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupMembershipRemoveUser(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyGroupMembership should be a sufficient permission to perform
         * the user removal from a group */
        boolean isExpectSuccessRemoveUserFromGroup = isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        /* one extra group is needed which the normalUser is also a member of */
        final ExperimenterGroup otherGroup = newGroupAddUser("rwr-r-", normalUser.userId);
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroupMembership.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter user = new ExperimenterI(normalUser.userId, false);
        try {
            iAdmin.removeGroups(user, Collections.singletonList(otherGroup));
            Assert.assertTrue(isExpectSuccessRemoveUserFromGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessRemoveUserFromGroup);
        }
    }

    /**
     * Test that light admin can make a user an owner of a group
     * when the light admin has only the <tt>ModifyGroupMembership</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroupMembership</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupMembershipMakeOwner(boolean isPrivileged, String groupPermissions) throws Exception {
        /* the permModifyGroupMembership should be a sufficient permission to perform
         * the setting of a new group owner */
        boolean isExpectSuccessMakeOwnerOfGroup= isPrivileged;
        final EventContext normalUser = newUserAndGroup(groupPermissions);
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroupMembership.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter user = new ExperimenterI(normalUser.userId, false);
        final ExperimenterGroup group = new ExperimenterGroupI(normalUser.groupId, false);
        try {
            iAdmin.setGroupOwner(group, user);
            Assert.assertTrue(isExpectSuccessMakeOwnerOfGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessMakeOwnerOfGroup);
        }
    }

    /**
     * Test that light admin can unset a user to be an owner of a group
     * when the light admin has only the <tt>ModifyGroupMembership</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroupMembership</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupMembershipUnsetOwner(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyGroupMembership should be a sufficient permission to perform
         * the unsetting of a new group owner */
        boolean isExpectSuccessUnsetOwnerOfGroup= isPrivileged;
        /* set up the normalUser and make him an Owner by passing "true" in the
         * newUserAndGroup method argument */
        final EventContext normalUser = newUserAndGroup(groupPermissions, true);
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroupMembership.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter user = new ExperimenterI(normalUser.userId, false);
        final ExperimenterGroup group = new ExperimenterGroupI(normalUser.groupId, false);
        try {
            iAdmin.unsetGroupOwner(group, user);
            Assert.assertTrue(isExpectSuccessUnsetOwnerOfGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessUnsetOwnerOfGroup);
        }
        /* check that the normalUser was unset as the owner of group when appropriate */
        if (isExpectSuccessUnsetOwnerOfGroup) {
            Assert.assertTrue(iAdmin.getLeaderOfGroupIds(user).isEmpty());
        } else {
            Assert.assertEquals((long) iAdmin.getLeaderOfGroupIds(user).get(0), group.getId().getValue());
        }
    }

    /**
     * Test that light admin can create a new user
     * when the light admin has only the <tt>ModifyUser</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyUser</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyUserCreate(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyUser should be a sufficient permission to perform
         * the creation of a new user */
        boolean isExpectSuccessCreateUser= isPrivileged;
        final long newGroupId = newUserAndGroup(groupPermissions).groupId;
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyUser.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter newUser = new ExperimenterI();
        newUser.setOmeName(omero.rtypes.rstring(UUID.randomUUID().toString()));
        newUser.setFirstName(omero.rtypes.rstring("August"));
        newUser.setLastName(omero.rtypes.rstring("Köhler"));
        newUser.setLdap(omero.rtypes.rbool(false));
        try {
            final long userGroupId = iAdmin.getSecurityRoles().userGroupId;
            final List<ExperimenterGroup> groups = ImmutableList.<ExperimenterGroup>of(new ExperimenterGroupI(userGroupId, false));
            iAdmin.createExperimenter(newUser, new ExperimenterGroupI(newGroupId, false), groups);
            Assert.assertTrue(isExpectSuccessCreateUser);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessCreateUser);
        }
    }

    /**
     * Test that light admin can edit an existing user
     * when the light admin has only the <tt>ModifyUser</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyUser</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyUserEdit(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyUser should be a sufficient permission to perform
         * the editing of a user */
        boolean isExpectSuccessEditUser= isPrivileged;
        final long newUserId = newUserAndGroup(groupPermissions).userId;
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyUser.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        final Experimenter newUser = (Experimenter) iQuery.get("Experimenter", newUserId);
        newUser.setConfig(ImmutableList.of(new NamedValue("color", "green")));
        try {
            iAdmin.updateExperimenter(newUser);
            Assert.assertTrue(isExpectSuccessEditUser);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessEditUser);
        }
    }

    /**
     * Test that light admin can create a new group
     * when the light admin has only the <tt>ModifyGroup</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroup</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupCreate(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyGroup should be a sufficient permission to perform
         * a group creation */
        boolean isExpectSuccessCreateGroup = isPrivileged;
        final ExperimenterGroup newGroup = new ExperimenterGroupI();
        newGroup.setLdap(omero.rtypes.rbool(false));
        newGroup.setName(omero.rtypes.rstring(UUID.randomUUID().toString()));
        newGroup.getDetails().setPermissions(new PermissionsI(groupPermissions));
        /* set up the permissions for the light admin */
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroup.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        try {
            iAdmin.createGroup(newGroup);
            Assert.assertTrue(isExpectSuccessCreateGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessCreateGroup);
        }
    }

    /**
     * Test that light admin can edit an existing group
     * when the light admin has only the <tt>ModifyGroup</tt> privilege.
     * @param isPrivileged if to test a user who has the <tt>ModifyGroup</tt> privilege
     * @param groupPermissions if to test the effect of group permission level
     * @throws Exception unexpected
     */
    @Test(dataProvider = "isPrivileged cases")
    public void testModifyGroupEdit(boolean isPrivileged,
            String groupPermissions) throws Exception {
        /* the permModifyGroup should be a sufficient permission to perform
         * group editing */
        boolean isExpectSuccessEditGroup = isPrivileged;
        /* set up the new group as Read-Write as the downgrade (edit) to all group
         * types by the light admin will be tested later in the test */
        final long newGroupId = newUserAndGroup("rwrw--").groupId;
        /* set up the permissions for the light admin */
        List<String> permissions = new ArrayList<String>();
        if (isPrivileged) permissions.add(AdminPrivilegeModifyGroup.value);
        final EventContext lightAdmin;
        lightAdmin = loginNewAdmin(true, permissions);
        /* light admin will downgrade the group to all possible permission levels and
         * also will edit the ldap settings */
        final ExperimenterGroup newGroup = (ExperimenterGroup) iQuery.get("ExperimenterGroup", newGroupId);
        newGroup.getDetails().setPermissions(new PermissionsI(groupPermissions));
        newGroup.setLdap(omero.rtypes.rbool(true));
        try {
            iAdmin.updateGroup(newGroup);
            Assert.assertTrue(isExpectSuccessEditGroup);
        } catch (ServerError se) {
            Assert.assertFalse(isExpectSuccessEditGroup);
        }
    }

    /**
     * @return test cases for File Attachment workflow testFileAttachmentNoSudo
     */
    @DataProvider(name = "fileAttachment privileges cases")
    public Object[][] provideFileAttachmentPrivilegesCases() {
        int index = 0;
        final int PERM_CHOWN = index++;
        final int PERM_WRITEOWNED = index++;
        final int PERM_WRITEFILE = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean permChown : booleanCases) {
                for (final boolean permWriteOwned : booleanCases) {
                    for (final boolean permWriteFile : booleanCases) {
                        for (final String groupPerms : permsCases) {
                            final Object[] testCase = new Object[index];
                            testCase[PERM_CHOWN] = permChown;
                            testCase[PERM_WRITEOWNED] = permWriteOwned;
                            testCase[PERM_WRITEFILE] = permWriteFile;
                            testCase[GROUP_PERMS] = groupPerms;
                            //DEBUG if (permChown == true && permWriteOwned == true && permWriteFile == true && groupPerms.equals("rwr---"))
                            testCases.add(testCase);
                        }
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return test cases for testCreateLinkImportSudo and testEdit
     */
    @DataProvider(name = "isSudoing and WriteOwned privileges cases")
    public Object[][] provideIsSudoingAndWriteOwned() {
        int index = 0;
        final int IS_SUDOING = index++;
        final int PERM_WRITEOWNED = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean isSudoing : booleanCases) {
                for (final boolean permWriteOwned : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        if (isSudoing && permWriteOwned)
                            /* not an interesting case */
                            continue;
                        testCase[IS_SUDOING] = isSudoing;
                        testCase[PERM_WRITEOWNED] = permWriteOwned;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG if (isSudoing == true && permWriteOwned == true)
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return test cases for testDelete
     */
    @DataProvider(name = "isSudoing and Delete privileges cases")
    public Object[][] provideIsSudoingAndDeleteOwned() {
        int index = 0;
        final int IS_SUDOING = index++;
        final int PERM_DELETEOWNED = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean isSudoing : booleanCases) {
                for (final boolean permDeleteOwned : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        if (isSudoing && permDeleteOwned)
                            /* not an interesting case */
                            continue;
                        testCase[IS_SUDOING] = isSudoing;
                        testCase[PERM_DELETEOWNED] = permDeleteOwned;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG if (isSudoing == true && permDeleteOwned == true)
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return isSudoing, Chgrp and DeleteOwned test cases for testChgrp
     */
    @DataProvider(name = "isSudoing and Chgrp privileges cases")
    public Object[][] provideIsSudoingAndChgrpOwned() {
        int index = 0;
        final int IS_SUDOING = index++;
        final int PERM_CHGRP = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean isSudoing : booleanCases) {
                for (final boolean permChgrp : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        /* no test cases are excluded here, because isSudoing
                         * is in a sense acting to annule Chgrp permission
                         * which is tested in the testChgrp and is an interesting case.*/
                        testCase[IS_SUDOING] = isSudoing;
                        testCase[PERM_CHGRP] = permChgrp;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG  if (isSudoing == true && permChgrp == true)
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return isSudoing and Chown test cases for testChown
     */
    @DataProvider(name = "isSudoing and Chown privileges cases")
    public Object[][] provideIsSudoingAndChown() {
        int index = 0;
        final int IS_SUDOING = index++;
        final int PERM_CHOWN = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean isSudoing : booleanCases) {
                for (final boolean permChown : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        /* no test cases are excluded here, because isSudoing
                         * is in a sense acting to annule Chown permission
                         * which is tested in the testChown and is an interesting case.*/
                        testCase[IS_SUDOING] = isSudoing;
                        testCase[PERM_CHOWN] = permChown;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG  if (isSudoing == true && permChown == true)
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return provide WriteOwned, WriteFile, WriteManagedRepo and Chown cases
     * for testImporterAsNoSudoChownOnly
     */
    @DataProvider(name = "WriteOwned, WriteFile, WriteManagedRepo and Chown privileges cases")
    public Object[][] provideWriteOwnedWriteFileWriteManagedRepoAndChown() {
        int index = 0;
        final int PERM_WRITEOWNED = index++;
        final int PERM_WRITEFILE = index++;
        final int PERM_WRITEMANAGEDREPO = index++;
        final int PERM_CHOWN = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean permWriteOwned : booleanCases) {
                for (final boolean permWriteFile : booleanCases) {
                    for (final boolean permWriteManagedRepo : booleanCases) {
                        for (final boolean permChown : booleanCases) {
                            for (final String groupPerms : permsCases) {
                                final Object[] testCase = new Object[index];
                                if (!permWriteOwned && !permWriteFile)
                                    /* not an interesting case */
                                    continue;
                                if (!permWriteOwned && !permWriteManagedRepo)
                                    /* not an interesting case */
                                    continue;
                                if (!permWriteOwned && !permWriteFile && !permWriteManagedRepo)
                                    /* not an interesting case */
                                    continue;
                                testCase[PERM_WRITEOWNED] = permWriteOwned;
                                testCase[PERM_WRITEFILE] = permWriteFile;
                                testCase[PERM_WRITEMANAGEDREPO] = permWriteManagedRepo;
                                testCase[PERM_CHOWN] = permChown;
                                testCase[GROUP_PERMS] = groupPerms;
                                // DEBUG if (permWriteOwned == true && permWriteFile == true && permWriteManagedRepo == true && permChown == true)
                                testCases.add(testCase);
                            }
                        }
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return WriteOwned and Chown test cases for
     * testLinkNoSudo and testROIAndRenderingSettingsNoSudo
     */
    @DataProvider(name = "WriteOwned and Chown privileges cases")
    public Object[][] provideWriteOwnedAndChown() {
        int index = 0;
        final int PERM_WRITEOWNED = index++;
        final int PERM_CHOWN = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean permWriteOwned : booleanCases) {
                for (final boolean permChown : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        if (!permWriteOwned && !permChown)
                            /* not an interesting case */
                            continue;
                        testCase[PERM_WRITEOWNED] = permWriteOwned;
                        testCase[PERM_CHOWN] = permChown;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG if (permWriteOwned == true && permChown == true && groupPerms.equals("rwr---"))
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return Chgrp and Chown test cases for testImporterAsNoSudoChgrpChown
     */
    @DataProvider(name = "Chgrp and Chown privileges cases")
    public Object[][] provideChgrpAndChown() {
        int index = 0;
        final int PERM_CHGRP = index++;
        final int PERM_CHOWN = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean permChgrp : booleanCases) {
                for (final boolean permChown : booleanCases) {
                    for (final String groupPerms : permsCases) {
                        final Object[] testCase = new Object[index];
                        /* No test cases are excluded here, because Chgrp
                         * and Chown are two separate steps which can work
                         * independently on each other and both are tested
                         * in the test.*/
                        testCase[PERM_CHGRP] = permChgrp;
                        testCase[PERM_CHOWN] = permChown;
                        testCase[GROUP_PERMS] = groupPerms;
                        // DEBUG if (permChgrp == true && permChown == true)
                        testCases.add(testCase);
                    }
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }

    /**
     * @return isPrivileged test cases. The isPrivileged parameter translates into one
     * tested privilege in particular tests (for example in testScriptUpload isPrivileged
     * concerns WriteScriptRepo privilege specifically)
     */
    @DataProvider(name = "isPrivileged cases")
    public Object[][] provideIsPrivilegesCases() {
        int index = 0;
        final int IS_PRIVILEGED = index++;
        final int GROUP_PERMS = index++;

        final boolean[] booleanCases = new boolean[]{false, true};
        final String[] permsCases = new String[]{"rw----", "rwr---", "rwra--", "rwrw--"};
        final List<Object[]> testCases = new ArrayList<Object[]>();

            for (final boolean isPrivileged : booleanCases) {
                for (final String groupPerms : permsCases) {
                    final Object[] testCase = new Object[index];
                    testCase[IS_PRIVILEGED] = isPrivileged;
                    testCase[GROUP_PERMS] = groupPerms;
                    // DEBUG if (isPrivileged == true)
                    testCases.add(testCase);
                }
            }
        return testCases.toArray(new Object[testCases.size()][]);
    }
}
