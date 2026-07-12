/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

// SettingsMetrics.swift
// LimeIME-iOS
//
// Shared settings-app layout constants. Keep repeated UI metrics here so
// Settings screens stay visually consistent across iPhone and iPad.

import CoreGraphics

enum SettingsMetrics {
    static let contentMaxWidth: CGFloat = 560

    static let pageHorizontalPadding: CGFloat = 24
    static let titleHorizontalPadding: CGFloat = 20
    static let titleTopPadding: CGFloat = 16
    static let titleBottomPadding: CGFloat = 8

    static let groupedSectionHorizontalPadding: CGFloat = 16
    static let groupedSectionCornerRadius: CGFloat = 10

    static let modalPadding: CGFloat = 24
    static let modalCornerRadius: CGFloat = 12
    static let globalModalCornerRadius: CGFloat = 14
    static let modalSpacing: CGFloat = 12
    static let modalShadowRadius: CGFloat = 8

    static let rowVerticalPadding: CGFloat = 11
    static let statusVerticalPadding: CGFloat = 10
    static let statusHorizontalPadding: CGFloat = 16

    static let floatingActionPadding: CGFloat = 16
    static let floatingActionOuterPadding: CGFloat = 20
    static let floatingActionShadowRadius: CGFloat = 4
    static let floatingActionShadowY: CGFloat = 2

    static let formHeaderLeadingPadding: CGFloat = 4
    static let formHeaderTopPadding: CGFloat = 20
    static let dbActionBottomSpacing: CGFloat = 18
    static let progressBarWidth: CGFloat = 180

    static let scoreFieldWidth: CGFloat = 64
    static let detailToolbarButtonSize: CGFloat = 44
    static let listCountColumnWidth: CGFloat = 48

    static let setupLogoSize: CGFloat = 92
    static let setupFallbackLogoSize: CGFloat = 60
    static let setupFallbackLogoPadding: CGFloat = 10
    static let setupLogoCornerRadius: CGFloat = 18
    static let setupStepIconWidth: CGFloat = 32
    static let setupStepSpacing: CGFloat = 16
    static let setupHeroTopPadding: CGFloat = 20
    static let setupHeroSpacing: CGFloat = 16
    static let setupWordmarkFontSize: CGFloat = 30
    static let setupTitleFontSize: CGFloat = 28
    static let setupListSpacing: CGFloat = 16
    static let setupBottomPadding: CGFloat = 32

    static let aboutFooterSpacing: CGFloat = 16
    static let aboutFooterTopPadding: CGFloat = 10
    static let aboutChipSpacing: CGFloat = 10
    static let aboutChipCornerRadius: CGFloat = 14
    static let aboutChipInnerSpacing: CGFloat = 7
    static let aboutChipVerticalPadding: CGFloat = 14
    static let aboutChipHorizontalPadding: CGFloat = 8
    static let aboutCopyrightTopPadding: CGFloat = 6

    // Rating prompt (§4.4) — tonal review-invitation card + dismiss ×.
    static let ratingCardSpacing: CGFloat = 14
    static let ratingCardInnerSpacing: CGFloat = 6
    static let ratingCardVerticalPadding: CGFloat = 16
    static let ratingCardHorizontalPadding: CGFloat = 18
    static let ratingCardCornerRadius: CGFloat = 16
    static let ratingTitleFontSize: CGFloat = 17
    static let ratingSubtitleFontSize: CGFloat = 14
    static let ratingStarSpacing: CGFloat = 4
    static let ratingStarSize: CGFloat = 15
    static let ratingChevronFontSize: CGFloat = 14
    static let ratingDismissSize: CGFloat = 24
    static let ratingDismissGlyphSize: CGFloat = 11
    static let ratingDismissInset: CGFloat = 8

    static let switchTrackWidth: CGFloat = 30
    static let switchTrackHeight: CGFloat = 18
    static let switchThumbSize: CGFloat = 14
    static let switchThumbTrailingPadding: CGFloat = 2
    static let switchShadowRadius: CGFloat = 1
    static let switchShadowY: CGFloat = 1

    static let invisibleProbeSize: CGFloat = 1
    static let invisibleProbeOpacity: CGFloat = 0.01
    static let titleSectionHeight: CGFloat = 60

    static let imBadgeSize: CGFloat = 30
    static let imBadgeCornerRadius: CGFloat = 7
    static let imBadgeFontSize: CGFloat = 15
    static let imRowSpacing: CGFloat = 12

    static let prefIconWidth: CGFloat = 22
    static let prefIconSpacing: CGFloat = 12

    static let familyIconSize: CGFloat = 28
    static let familyIconCornerRadius: CGFloat = 6
    static let installButtonWidth: CGFloat = 64
    static let installProgressWidth: CGFloat = 60
    static let importingButtonWidth: CGFloat = 80
    static let installedButtonWidth: CGFloat = 80
}
