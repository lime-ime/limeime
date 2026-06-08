# Manual Verification Checklist

Use this checklist before merging the manual-setup branch to master.

## Content Verification

### Pages Complete
- [x] Root navigation (index.md, faq.md) — 2 pages
- [x] Quick Start (overview, setup, first-ime, troubleshooting) — 4 pages
- [x] IME Management (overview, download, import, list, editor) — 5 pages
- [x] Keyboard Layouts (overview, english, chinese, symbols, popup, ipad) — 6 pages
- [x] Preferences (overview, appearance, behavior, han-convert, reverse-lookup, backup) — 6 pages
- [x] Advanced (overview, custom-ime, database, learning) — 4 pages
- [x] Documentation (README, screenshot manifest, jekyll setup) — 3 pages
- [x] **Total: 29+ pages**

### Content Quality
- [ ] All pages have front matter (title, optional description)
- [ ] All internal links verified (no 404s)
- [ ] All code examples formatted correctly
- [ ] All tables properly rendered
- [ ] Traditional Chinese spelling consistent (繁體中文)
- [ ] No English content in user-facing pages
- [ ] No placeholder text remaining ("TODO", "FIXME", etc.)

### Language & Tone
- [ ] All content in Traditional Chinese
- [ ] Terminology consistent across pages
- [ ] User-friendly, non-technical where possible
- [ ] Platform-specific sections clearly marked
- [ ] Links use proper markdown syntax

## Structure Verification

### Folder Organization
- [x] `/manual/` at repository root
- [x] English-only folder names (no Chinese characters)
- [x] Proper subdirectories: quick-start/, ime-management/, etc.
- [x] Assets folder structure: assets/screenshots/{android,ios,ipad}/
- [x] No extra folders created at root

### File Naming
- [x] All files in English lowercase with hyphens
- [x] `.md` extension for all markdown files
- [x] Consistent naming patterns per section
- [x] No spaces in filenames

### Navigation
- [ ] Root index.md links to all sections
- [ ] Each section has overview.md
- [ ] "Next steps" links at bottom of each page
- [ ] FAQ page accessible from all sections
- [ ] README.md provides multiple entry points

## Configuration Verification

### Jekyll Setup
- [x] `_config.yml` properly updated
- [x] `Gemfile` with all required dependencies
- [x] `jekyll-theme-primer` configured
- [x] Plugins enabled: jekyll-optional-front-matter, jekyll-sitemap
- [x] Build defaults configured

### GitHub Actions
- [x] `.github/workflows/jekyll-build.yml` created
- [x] Workflow triggers on manual-setup push
- [x] Artifact upload configured
- [x] GitHub Pages deployment ready
- [x] PR preview comments enabled

### Documentation
- [x] `JEKYLL_SETUP.md` explains configuration
- [x] `SCREENSHOT_MANIFEST.md` outlines screenshot plan
- [x] `MANUAL_VERIFICATION_CHECKLIST.md` (this file)

## Git Verification

### Commits
- [x] Atomic commits per phase
- [x] Clear commit messages
- [x] All files properly staged
- [ ] No large binary files committed
- [ ] No temporary files in repository

### Branch Status
- [ ] Branch: `manual-setup`
- [ ] Tracking commits: 8
- [ ] Diff from master: Manual folder + config + workflows
- [ ] Ready for merge review

## Pre-Merge Checklist

### Before Merging to Master

1. **Code Review**
   - [ ] Manager reviews all 29 pages
   - [ ] No critical issues found
   - [ ] Configuration approved

2. **Testing**
   - [ ] GitHub Actions workflow runs successfully
   - [ ] Build artifact verified
   - [ ] No build errors or warnings
   - [ ] Links resolve correctly

3. **Documentation**
   - [ ] README updated with manual link
   - [ ] JEKYLL_SETUP.md complete
   - [ ] Merge strategy documented

4. **Cleanup**
   - [ ] CRLF warnings addressed (convert to LF if needed)
   - [ ] No conflict markers in files
   - [ ] No debug comments

5. **Final Check**
   - [ ] `_config.yml` valid YAML
   - [ ] All markdown properly formatted
   - [ ] No broken references
   - [ ] Screenshot folder structure ready

## Merge Process

### Step 1: Prepare
```bash
# Ensure clean working directory
git status

# Update branch from master
git fetch origin
git rebase origin/master
```

### Step 2: Verify Again
- [ ] Run Jekyll build locally (if Ruby available)
- [ ] Check workflow would trigger successfully
- [ ] Final link verification

### Step 3: Create PR
```bash
# Create PR from manual-setup to master
gh pr create --title "Add LIME User Manual" \
  --body "Phase 1-4 complete: 29 pages, Jekyll config, GitHub Actions setup"
```

### Step 4: Merge
Once approved:
```bash
# Merge PR (squash or regular)
gh pr merge [PR_NUMBER] --merge
```

### Step 5: Cleanup
```bash
# Delete feature branch
git push origin --delete manual-setup

# Update local
git pull origin master
```

## Post-Merge Tasks

### After Merging to Master

- [ ] Verify GitHub Pages builds (check Actions)
- [ ] Test live site: https://github.com/lime-ime/limeime
- [ ] Update README.md with manual link
- [ ] Create release notes mentioning manual
- [ ] Announce manual availability
- [ ] Begin screenshot capture phase

### Phase 5+ Tasks

- **Phase 5B: Screenshot Capture** (~2-3 weeks)
  - Capture ~40-50 screenshots
  - Add to `/manual/assets/screenshots/`
  - Update pages with image references

- **Phase 6: Launch**
  - Final testing
  - Enable GitHub Pages in repository settings
  - Public announcement

## Rollback Plan

If critical issues found after merge:

1. **Create hotfix branch**
   ```bash
   git checkout -b hotfix/manual-issues
   ```

2. **Fix issues**
   - Edit affected markdown files
   - Test changes

3. **Merge hotfix**
   ```bash
   git checkout master
   git merge hotfix/manual-issues
   ```

## Success Criteria

Manual is ready for production when:
- ✅ All 29 pages present and linked
- ✅ Jekyll builds without errors
- ✅ GitHub Pages shows correct content
- ✅ All internal links work (no 404s)
- ✅ Traditional Chinese content correct
- ✅ Screenshots placeholder ready
- ✅ Documentation complete

## Sign-Off

- [ ] Content review: _________________ Date: _______
- [ ] Configuration review: _________________ Date: _______
- [ ] Final approval: _________________ Date: _______

---

**Status:** Ready for verification and merge
**Last Updated:** 2026-06-08
**Contact:** Submit issues to GitHub or contact LIME maintainers
