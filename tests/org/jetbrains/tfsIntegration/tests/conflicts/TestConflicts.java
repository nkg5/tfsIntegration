/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.tfsIntegration.tests.conflicts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.tfsIntegration.core.tfs.WorkspaceInfo;
import org.jetbrains.tfsIntegration.core.tfs.conflicts.*;
import org.jetbrains.tfsIntegration.exceptions.TfsException;
import org.jetbrains.tfsIntegration.stubs.versioncontrol.repository.Conflict;
import org.jetbrains.tfsIntegration.tests.TFSTestCase;
import org.jetbrains.tfsIntegration.ui.ContentTriplet;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class TestConflicts extends TFSTestCase {

  protected static final String BASE_FILENAME = "base.txt";
  protected static final String YOURS_FILENAME = "yours.txt";
  protected static final String THEIRS_FILENAME = "theirs.txt";
  protected static final String MERGED_FILENAME = "merged.txt";

  protected static final String BASE_CONTENT = "base_content";
  protected static final String THEIRS_CONTENT = "theirs_content";
  protected static final String YOURS_CONTENT = "yours_content";
  protected static final String MERGED_CONTENT = "merged_content";

  private enum Resolution {
    AcceptYours, AcceptTheirs, Merge
  }

  protected abstract boolean canMerge();

  @SuppressWarnings({"MethodMayBeStatic"})
  protected int getExpectedConflictsCount() {
    return 1;
  }

  protected abstract void preparePaths();

  protected abstract void prepareBaseRevision();

  protected abstract void prepareTargetRevision() throws VcsException, IOException;

  protected abstract void makeLocalChanges() throws IOException, VcsException;

  protected abstract void checkConflictProperties(Conflict conflict) throws TfsException;

  protected abstract void checkResolvedYoursState() throws VcsException;

  protected abstract void checkResolvedTheirsState() throws VcsException;

  protected abstract void checkResolvedMergeState() throws VcsException;

  @Nullable
  protected String getExpectedBaseContent() {
    Assert.fail("not supported");
    return null;
  }

  @Nullable
  protected String getExpectedYoursContent() {
    Assert.fail("not supported");
    return null;
  }

  @Nullable
  protected String getExpectedTheirsContent() {
    Assert.fail("not supported");
    return null;
  }

  @Nullable
  protected abstract String mergeContent();

  @Nullable
  protected abstract String mergeName() throws TfsException;

  private void doTest(final Resolution resolution) throws VcsException, IOException {
    ConflictsEnvironment.setResolveConflictsHandler(new ConflictsHandler() {
      public void resolveConflicts(final ResolveConflictHelper resolveConflictHelper) throws TfsException {
        List<Conflict> conflicts = resolveConflictHelper.getConflicts();
        Assert.assertEquals("Expected conflicts count differs: ", getExpectedConflictsCount(), conflicts.size());
        for (Conflict conflict : conflicts) {
          checkConflictProperties(conflict);
          try {
            if (resolution == Resolution.AcceptYours) {
              resolveConflictHelper.acceptYours(conflict);
            }
            else if (resolution == Resolution.AcceptTheirs) {
              resolveConflictHelper.acceptTheirs(conflict);
            }
            else {
              Assert.assertEquals(canMerge(), ResolveConflictHelper.canMerge(conflict));
              if (canMerge()) {
                resolveConflictHelper.acceptMerge(conflict);
              }
              else {
                resolveConflictHelper.skip(conflict);
              }
            }
          }
          catch (VcsException e) {
            Assert.fail(e.getMessage());
          }
          catch (IOException e) {
            Assert.fail(e.getMessage());
          }
        }
      }
    });

    ConflictsEnvironment.setNameMerger(new NameMerger() {
      @Nullable
      public String mergeName(final WorkspaceInfo workspace, final Conflict conflict) {
        try {
          return TestConflicts.this.mergeName();
        }
        catch (TfsException e) {
          Assert.fail(e.getMessage());
          return null;
        }
      }
    });

    ConflictsEnvironment.setContentMerger(new ContentMerger() {
      public void mergeContent(final Conflict conflict,
                               final ContentTriplet contentTriplet,
                               final Project project,
                               final VirtualFile targetFile,
                               final String localPathToDisplay) throws IOException, VcsException {
        Assert.assertEquals(getExpectedBaseContent(), contentTriplet.baseContent);
        Assert.assertEquals(getExpectedYoursContent(), contentTriplet.localContent);
        Assert.assertEquals(getExpectedTheirsContent(), contentTriplet.serverContent);

        ReadOnlyAttributeUtil.setReadOnlyAttribute(targetFile, false);
        setFileContent(targetFile, TestConflicts.this.mergeContent());
      }
    });

    preparePaths();
    prepareBaseRevision();
    commit(getChanges().getChanges(), "Base revision");
    prepareTargetRevision();
    commit(getChanges().getChanges(), "Target revision");
    updateTo(1);
    makeLocalChanges();

    updateTo(0);
    if (resolution == Resolution.AcceptYours) {
      checkResolvedYoursState();
    }
    else if (resolution == Resolution.AcceptTheirs) {
      checkResolvedTheirsState();
    }
    else {
      if (canMerge()) {
        checkResolvedMergeState();
      }
    }
  }

  protected void testAcceptYours() throws VcsException, IOException {
    doTest(Resolution.AcceptYours);
  }

  protected void testAcceptTheirs() throws VcsException, IOException {
    doTest(Resolution.AcceptTheirs);
  }

  protected void testAcceptMerge() throws VcsException, IOException {
    doTest(Resolution.Merge);
  }

}