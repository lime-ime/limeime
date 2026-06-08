# Jekyll Setup Guide

This document explains the Jekyll configuration for the LIME User Manual on GitHub Pages.

## Overview

- **Theme:** `jekyll-theme-primer` (GitHub's default)
- **Source:** Root directory of repository
- **Build Output:** `_site/` directory
- **Deployment:** GitHub Pages (automatic via Actions)
- **Markdown Engine:** kramdown

## Configuration Files

### `_config.yml`
Main Jekyll configuration:
- Theme and plugins
- Site metadata (title, description)
- Build defaults and collections
- Navigation metadata

### `Gemfile`
Ruby dependencies:
- Jekyll and theme gems
- Required plugins
- GitHub Pages compatible versions

### `.github/workflows/jekyll-build.yml`
GitHub Actions workflow:
- Triggers: push to `manual-setup` branch, PRs to `master`
- Builds Jekyll site
- Uploads artifact for preview
- Deploys to GitHub Pages on successful build

## Local Testing

### Prerequisites
```bash
# Install Ruby 3.2+
ruby --version

# Install Bundler
gem install bundler
```

### Setup
```bash
# Install dependencies
bundle install

# Build site
bundle exec jekyll build

# Serve locally
bundle exec jekyll serve --livereload
# Visit http://localhost:4000
```

### Troubleshooting

**Error: "Could not find gem 'jekyll'"**
```bash
bundle install
bundle update
```

**Error: "Permission denied" on build**
```bash
# Clear existing build
rm -rf _site/
# Try again with sudo (if needed)
```

## Directory Structure

```
repository/
├── _config.yml          # Jekyll configuration
├── Gemfile              # Ruby dependencies
├── Gemfile.lock         # Locked versions (generated)
├── _site/               # Build output (generated)
│   ├── index.html
│   ├── LICENSE/
│   ├── manual/
│   │   ├── index.html
│   │   ├── faq.html
│   │   ├── quick-start/
│   │   ├── ime-management/
│   │   ├── keyboard-layouts/
│   │   ├── preferences/
│   │   ├── advanced/
│   │   └── assets/
│   └── ...
├── .github/
│   └── workflows/
│       └── jekyll-build.yml
├── manual/              # User manual source
│   ├── README.md
│   ├── index.md
│   ├── faq.md
│   ├── SCREENSHOT_MANIFEST.md
│   └── [sections]/
├── docs/                # Developer docs (untouched)
├── README.md            # Root readme
├── LICENSE.md
└── ...
```

## Build Process

### Local Build
1. `bundle install` — Install dependencies
2. `bundle exec jekyll build` — Build static site
3. Output written to `_site/`

### GitHub Actions Build
1. Checkout code
2. Setup Ruby 3.2
3. Install gems from Gemfile
4. Run `jekyll build` with production settings
5. Upload artifact to Actions
6. Deploy to GitHub Pages (branch: `manual-setup`)

## Site Structure

### Homepage
- **Source:** `README.md` (root) or `index.md`
- **URL:** `/`
- **Purpose:** Landing page with links to manual

### Manual Section
- **Source:** `manual/` directory
- **URL:** `/manual/`
- **Structure:**
  - `manual/index.md` → `/manual/` (navigation)
  - `manual/faq.md` → `/manual/faq.html`
  - `manual/quick-start/overview.md` → `/manual/quick-start/overview.html`
  - etc.

## Key Configuration Details

### Plugins
```yaml
plugins:
  - jekyll-optional-front-matter  # Allow frontmatter to be optional
  - jekyll-sitemap                # Auto-generate sitemap.xml
```

### Defaults
- All pages use `default` layout
- Manual pages use `page` layout
- Markdown processed with kramdown

### Includes
- `LICENSE.md` — Included in build
- `_redirects` — For redirect rules (if needed)

### Excludes
- `.claude/` — Local planning files
- `.git/` — Git data
- `docs/` — Developer docs (kept separate)
- `LimeStudio/`, `LimeIME-iOS/`, `LimeIME-Android/` — Source code

## Deployment

### Automatic Deployment
When code is pushed to `manual-setup` branch:
1. GitHub Actions workflow triggers
2. Jekyll builds the site
3. Artifact uploaded
4. Site deployed to GitHub Pages

### Manual Deployment
To trigger manual build/deploy:
```bash
git push origin manual-setup
```

Monitor build progress:
1. Go to GitHub repository
2. Click "Actions" tab
3. Find "Build User Manual (Jekyll)" workflow
4. View build logs

## GitHub Pages Settings

Required repository settings (must be configured once):

1. **Pages source:**
   - Branch: `manual-setup` (or merge to master first)
   - Directory: `/ (root)`

2. **HTTPS:** Enable (recommended)

3. **Custom domain:** (optional)
   - If using custom domain, add CNAME file

## Navigation & Links

### Internal Links
Use relative links in markdown:
```markdown
[Quick Start](quick-start/overview.md)
[FAQ](/manual/faq.md)
![Screenshot](assets/screenshots/android/setup.png)
```

Jekyll automatically:
- Converts `.md` → `.html`
- Resolves relative paths
- Preserves `/manual/` prefix

### External Links
```markdown
[GitHub](https://github.com/lime-ime/limeime)
```

## Performance Optimization

### Build Time
- Initial: ~3–5 seconds
- Incremental: ~1–2 seconds
- GitHub Actions: ~10–15 seconds (including setup)

### Site Size
- Manual pages: ~2–3 MB HTML
- Screenshots: ~20–50 MB (when added)
- Total: ~50–100 MB (average site)

## Troubleshooting

### Build Fails in Actions
Check `.github/workflows/jekyll-build.yml` logs:
1. Go to Actions tab
2. Find failed workflow
3. View "Build Jekyll site" step output

### Pages Not Found (404)
- Check markdown filenames match URLs
- Verify YAML frontmatter syntax
- Ensure relative links are correct

### Styles Not Loading
- Run `bundle exec jekyll clean`
- Rebuild: `bundle exec jekyll build`
- Clear browser cache (Ctrl+Shift+Del)

### Links Broken After Deployment
- Use `baseurl: ""` (currently set)
- Avoid hardcoding domain names
- Use Jekyll's `| relative_url` filter if needed

## Future Enhancements

- [ ] Add custom CSS for better styling
- [ ] Create custom Jekyll layouts (header, nav)
- [ ] Add search functionality (Lunr)
- [ ] Add versioning support
- [ ] Set up automatic screenshot integration

## Resources

- [Jekyll Documentation](https://jekyllrb.com/docs/)
- [Primer Theme Docs](https://github.com/pages-themes/primer)
- [GitHub Pages Guide](https://docs.github.com/en/pages)
- [Markdown Reference](https://guides.github.com/features/mastering-markdown/)

## Contact

For questions about this setup, refer to:
- GitHub Issues: https://github.com/lime-ime/limeime/issues
- Documentation: This file (JEKYLL_SETUP.md)
