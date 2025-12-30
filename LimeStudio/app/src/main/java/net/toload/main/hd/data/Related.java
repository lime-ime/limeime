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
 * Represents a related phrase record for UI layer operations.
 * 
 * <p>This is an alias for {@link Mapping} used in the Manage Related Phrases UI
 * where related phrase terminology (pword, cword) is more appropriate.
 * 
 * <p>Field mappings:
 * <ul>
 *   <li>{@code getPword()} / {@code setPword()} - Parent word</li>
 *   <li>{@code getCword()} / {@code setCword()} - Child word (alias for word)</li>
 *   <li>{@code getUserscore()} / {@code setUserscore()} - User score (alias for score)</li>
 *   <li>{@code getBasescore()} / {@code setBasescore()} - Base score</li>
 * </ul>
 * 
 * <p>All fields and methods are inherited from {@link Mapping}.
 * 
 * @author LimeIME Team
 * @see Mapping
 */
public class Related extends Mapping {

	/** Empty constructor */
	public Related() {
		super();
	}

	/**
	 * Copy constructor.
	 * @param mapping The mapping to copy
	 */
	public Related(Mapping mapping) {
		super(mapping);
	}
}
