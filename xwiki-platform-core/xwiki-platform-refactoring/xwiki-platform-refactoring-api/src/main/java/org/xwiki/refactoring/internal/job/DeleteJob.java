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
package org.xwiki.refactoring.internal.job;

import java.util.Collection;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.refactoring.job.EntityJobStatus;
import org.xwiki.refactoring.job.EntityRequest;
import org.xwiki.refactoring.job.RefactoringJobs;
import org.xwiki.refactoring.job.question.EntitySelection;
import org.xwiki.security.authorization.Right;

/**
 * A job that can delete entities.
 *
 * @version $Id$
 * @since 7.2M1
 */
@Component
@Named(RefactoringJobs.DELETE)
public class DeleteJob extends AbstractEntityJobWithChecks<EntityRequest, EntityJobStatus<EntityRequest>>
{
    /** Context property used to save the batchId in the recycle bin. */
    private static final String BATCH_ID_PROPERTY = "BATCH_ID";

    @Inject
    private Execution execution;

    @Override
    public String getType()
    {
        return RefactoringJobs.DELETE;
    }

    @Override
    protected void process(Collection<EntityReference> entityReferences)
    {
        // Generate the batch ID and set it in the context.
        String deleteBatchId = UUID.randomUUID().toString();
        execution.getContext().setProperty(BATCH_ID_PROPERTY, deleteBatchId);

        super.process(entityReferences);

        // Clean the context when done.
        execution.getContext().setProperty(BATCH_ID_PROPERTY, null);
    }

    @Override
    protected void process(EntityReference entityReference)
    {
        // Dispatch the delete operation based on the entity type.

        switch (entityReference.getType()) {
            case DOCUMENT:
                process(new DocumentReference(entityReference));
                break;
            case SPACE:
                process(new SpaceReference(entityReference));
                break;
            default:
                this.logger.error("Unsupported entity type [{}].", entityReference.getType());
        }
    }

    private void process(DocumentReference documentReference)
    {
        if (this.request.isDeep() && isSpaceHomeReference(documentReference)) {
            process(documentReference.getLastSpaceReference());
        } else {
            maybeDelete(documentReference);
        }
    }

    private void process(SpaceReference spaceReference)
    {
        visitDocuments(spaceReference, new Visitor<DocumentReference>()
        {
            @Override
            public void visit(DocumentReference documentReference)
            {
                maybeDelete(documentReference);
            }
        });
    }

    private void maybeDelete(DocumentReference documentReference)
    {
        EntitySelection entitySelection = concernedEntities.get(documentReference);
        if (entitySelection != null && !entitySelection.isSelected()) {
            // TODO: handle entitySelection == null which means something is wrong
            this.logger.info("Skipping [{}] because it has been unselected.", documentReference);
        } else if (!this.modelBridge.exists(documentReference)) {
            this.logger.warn("Skipping [{}] because it doesn't exist.", documentReference);
        } else if (!hasAccess(Right.DELETE, documentReference)) {
            this.logger.error("You are not allowed to delete [{}].", documentReference);
        } else {
            this.modelBridge.delete(documentReference);
        }
    }
}
