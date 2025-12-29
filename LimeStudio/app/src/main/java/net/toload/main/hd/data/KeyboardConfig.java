/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
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

package net.toload.main.hd.data;

/**
 * Represents keyboard configuration data for UI components.
 * 
 * <p>This class is a simplified data transfer object (DTO) used primarily
 * in UI components to display and configure keyboard settings. It contains
 * keyboard layout information for different modes (IM, English, Symbol, etc.)
 * and their shift variants.
 * 
 * <p>This class does not contain any SQL code or database operations.
 * 
 * @author LimeIME Team
 */
public class KeyboardConfig extends Keyboard {
    // Compatibility wrapper: KeyboardConfig now extends the merged `Keyboard` model.
    // Keep this thin wrapper so existing code can continue compiling while
    // we update callsites to use `Keyboard` directly.
}
