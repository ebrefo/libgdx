/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.scenes.scene2d;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.DelayedRemovalArray;

/** 2D scene graph node that may contain other actors. Each child is given a chance to handle touch down and touch move events.
 * <p>
 * Actors have a z-order equal to the order they were inserted into the group. Actors inserted later will be drawn on top of
 * actors added earlier. Touch events that hit more than one actor are distributed to topmost actors first.
 * @author mzechner
 * @author Nathan Sweet */
public class Group extends Actor implements Cullable {
	private final DelayedRemovalArray<Actor> children = new DelayedRemovalArray(4);
	private final Matrix3 localTransform = new Matrix3();
	private final Matrix3 worldTransform = new Matrix3();
	private final Matrix4 batchTransform = new Matrix4();
	private final Matrix4 oldBatchTransform = new Matrix4();
	private boolean transform = true;
	private Rectangle cullingArea;
	private final Vector2 point = new Vector2();

	public Group () {
		this(null);
	}

	public Group (String name) {
		super(name);
	}

	public void act (float delta) {
		super.act(delta);
		DelayedRemovalArray<Actor> children = this.children;
		children.begin();
		for (int i = 0, n = children.size; i < n; i++)
			children.get(i).act(delta);
		children.end();
	}

	public void draw (SpriteBatch batch, float parentAlpha) {
		if (transform) applyTransform(batch);
		drawChildren(batch, parentAlpha);
		if (transform) resetTransform(batch);
	}

	/** Draws all children. {@link #applyTransform(SpriteBatch)} should be called before and {@link #resetTransform(SpriteBatch)}
	 * after this method if {@link #setTransform(boolean) transform} is true. if {@link #setTransform(boolean) transform} is false,
	 * children positions are temporarily offset by the group position when drawn. Avoids drawing children outside the
	 * {@link #setCullingArea(Rectangle) culling area}, if set. */
	protected void drawChildren (SpriteBatch batch, float parentAlpha) {
		parentAlpha *= getColor().a;
		DelayedRemovalArray<Actor> children = this.children;
		children.begin();
		if (cullingArea != null) {
			// Draw children only if inside culling area.
			if (transform) {
				for (int i = 0, n = children.size; i < n; i++) {
					Actor child = children.get(i);
					if (!child.isVisible()) continue;
					float x = child.getX();
					float y = child.getY();
					if (x <= cullingArea.x + cullingArea.width && y <= cullingArea.y + cullingArea.height
						&& x + child.getWidth() >= cullingArea.x && y + child.getHeight() >= cullingArea.y) {
						child.draw(batch, parentAlpha);
					}
				}
				batch.flush();
			} else {
				// No transform for this group, offset each child.
				float offsetX = getX();
				float offsetY = getY();
				setPosition(0, 0);
				for (int i = 0, n = children.size; i < n; i++) {
					Actor child = children.get(i);
					if (!child.isVisible()) continue;
					float x = child.getX();
					float y = child.getY();
					if (x <= cullingArea.x + cullingArea.width && y <= cullingArea.y + cullingArea.height
						&& x + child.getWidth() >= cullingArea.x && y + child.getHeight() >= cullingArea.y) {
						child.translate(offsetX, offsetY);
						child.draw(batch, parentAlpha);
						child.setPosition(x, y);
					}
				}
				setPosition(offsetX, offsetY);
			}
		} else {
			if (transform) {
				for (int i = 0, n = children.size; i < n; i++) {
					Actor child = children.get(i);
					if (!child.isVisible()) continue;
					child.draw(batch, parentAlpha);
				}
				batch.flush();
			} else {
				// No transform for this group, offset each child.
				float offsetX = getX();
				float offsetY = getY();
				setPosition(0, 0);
				for (int i = 0, n = children.size; i < n; i++) {
					Actor child = children.get(i);
					if (!child.isVisible()) continue;
					float x = child.getX();
					float y = child.getY();
					child.translate(offsetX, offsetY);
					child.draw(batch, parentAlpha);
					child.setPosition(x, y);
				}
				setPosition(offsetX, offsetY);
			}
		}
		children.end();
	}

	/** Transforms the SpriteBatch to this group's coordinate system. */
	protected void applyTransform (SpriteBatch batch) {
		updateTransform();
		batch.end();
		oldBatchTransform.set(batch.getTransformMatrix());
		batch.setTransformMatrix(batchTransform);
		batch.begin();
	}

	private void updateTransform () {
		Matrix3 temp = worldTransform;

		float originX = getOriginX();
		float originY = getOriginY();
		float rotation = getRotation();
		float scaleX = getScaleX();
		float scaleY = getScaleY();

		if (originX != 0 || originY != 0)
			localTransform.setToTranslation(originX, originY);
		else
			localTransform.idt();
		if (rotation != 0) localTransform.mul(temp.setToRotation(rotation));
		if (scaleX != 1 || scaleY != 1) localTransform.mul(temp.setToScaling(scaleX, scaleY));
		if (originX != 0 || originY != 0) localTransform.mul(temp.setToTranslation(-originX, -originY));
		localTransform.trn(getX(), getY());

		// Find the first parent that transforms.
		Group parentGroup = getParent();
		while (parentGroup != null) {
			if (parentGroup.transform) break;
			parentGroup = parentGroup.getParent();
		}

		if (parentGroup != null) {
			worldTransform.set(parentGroup.worldTransform);
			worldTransform.mul(localTransform);
		} else {
			worldTransform.set(localTransform);
		}

		batchTransform.set(worldTransform);
	}

	/** Restores the SpriteBatch transform to what it was before {@link #applyTransform(SpriteBatch)}. */
	protected void resetTransform (SpriteBatch batch) {
		batch.end();
		batch.setTransformMatrix(oldBatchTransform);
		batch.begin();
	}

	/** Children completely outside of this rectangle will not be drawn. This is only valid for use with unrotated and unscaled
	 * actors. */
	public void setCullingArea (Rectangle cullingArea) {
		this.cullingArea = cullingArea;
	}

	public Actor hit (float x, float y) {
		Array<Actor> children = this.children;
		for (int i = children.size - 1; i >= 0; i--) {
			Actor child = children.get(i);

			toChildCoordinates(child, x, y, point);

			Actor hit = child.hit(point.x, point.y);
			if (hit != null) return hit;
		}
		return super.hit(x, y);
	}

	/** Called when actors are added to or removed from the group. */
	protected void childrenChanged () {
	}

	/** Adds an actor as a child of this group. */
	public void addActor (Actor actor) {
		actor.remove();
		children.add(actor);
		actor.setParent(this);
		actor.setStage(getStage());
		childrenChanged();
	}

	/** Adds an actor as a child of this group, at a specific index. */
	public void addActorAt (int index, Actor actor) {
		actor.remove();
		children.insert(index, actor);
		actor.setParent(this);
		actor.setStage(getStage());
		childrenChanged();
	}

	/** Adds an actor as a child of this group, immediately before another child actor. */
	public void addActorBefore (Actor actorBefore, Actor actor) {
		actor.remove();
		int index = children.indexOf(actorBefore, true);
		children.insert(index, actor);
		actor.setParent(this);
		actor.setStage(getStage());
		childrenChanged();
	}

	/** Adds an actor as a child of this group, immediately after another child actor. */
	public void addActorAfter (Actor actorAfter, Actor actor) {
		actor.remove();
		int index = children.indexOf(actorAfter, true);
		if (index == children.size)
			children.add(actor);
		else
			children.insert(index + 1, actor);
		actor.setParent(this);
		actor.setStage(getStage());
		childrenChanged();
	}

	/** Removes an actor from this group. If the actor will not be used again and has actions, they should be
	 * {@link Actor#clearActions() cleared} so the actions will be returned to their
	 * {@link Action#setPool(com.badlogic.gdx.utils.Pool) pool}, if any. */
	public boolean removeActor (Actor actor) {
		if (!children.removeValue(actor, true)) return false;
		Stage stage = getStage();
		if (stage != null) stage.unfocus(actor);
		actor.setParent(null);
		actor.setStage(null);
		childrenChanged();
		return true;
	}

	/** Removes all actors from this group. */
	public void clear () {
		Array<Actor> children = this.children;
		for (int i = 0, n = children.size; i < n; i++) {
			Actor child = children.get(i);
			child.setStage(null);
			child.setParent(null);
		}
		children.clear();
		childrenChanged();
	}

	protected void setStage (Stage stage) {
		super.setStage(stage);
		Array<Actor> children = this.children;
		for (int i = 0, n = children.size; i < n; i++)
			children.get(i).setStage(stage);
	}

	/** Swaps two actors by index. Returns false if the swap did not occur because the indexes were out of bounds. */
	public boolean swapActor (int first, int second) {
		int maxIndex = children.size;
		if (first < 0 || first >= maxIndex) return false;
		if (second < 0 || second >= maxIndex) return false;
		children.swap(first, second);
		return true;
	}

	/** Swaps two actors. Returns false if the swap did not occur because the actors are not children of this group. */
	public boolean swapActor (Actor first, Actor second) {
		int firstIndex = children.indexOf(first, true);
		int secondIndex = children.indexOf(second, true);
		if (firstIndex == -1 || secondIndex == -1) return false;
		children.swap(firstIndex, secondIndex);
		return true;
	}

	/** Converts coordinates for this group to those of a descendant actor.
	 * @throws IllegalArgumentException if the specified actor is not a descendant of this group. */
	public void toDescendantCoordinates (Actor descendant, Vector2 localPoint) {
		Group parent = descendant.getParent();
		if (parent == null) throw new IllegalArgumentException("Child is not a descendant: " + descendant);
		// First convert to the actor's parent coordinates.
		if (parent != this) toDescendantCoordinates(parent, localPoint);
		// Then from each parent down to the descendant.
		Group.toChildCoordinates(descendant, localPoint.x, localPoint.y, localPoint);
	}

	/** Returns an ordered list of child actors in this group. */
	public Array<Actor> getActors () {
		return children;
	}

	/** When true (the default), the SpriteBatch is transformed so children are drawn in their parent's coordinate system. This has
	 * a performance impact because {@link SpriteBatch#flush()} must be done before and after the transform. If the actors in a
	 * group are not rotated or scaled, then the transform for the group can be set to false. In this case, each child's position
	 * will be offset by the group's position for drawing, causing the children to appear in the correct location even though the
	 * SpriteBatch has not been transformed. */
	public void setTransform (boolean transform) {
		this.transform = transform;
	}

	public boolean isTransform () {
		return transform;
	}

	/** Returns the actor with the given name in this group or its children, or null if not found. Note this scans potentially all
	 * actors in the group and any child groups, recursively. */
	public Actor findActor (String name) {
		if (name.equals(getName())) return this;
		Array<Actor> children = this.children;
		for (int i = 0, n = children.size; i < n; i++)
			if (name.equals(children.get(i).getName())) return children.get(i);
		for (int i = 0, n = children.size; i < n; i++) {
			Actor child = children.get(i);
			if (child instanceof Group) return ((Group)child).findActor(name);
		}
		return null;
	}

	/** Returns the stage's actor hierarchy as a string. */
	public String graphToString () {
		StringBuilder buffer = new StringBuilder(128);
		graphToString(buffer, this, 0);
		return buffer.toString();
	}

	private void graphToString (StringBuilder buffer, Actor actor, int level) {
		for (int i = 0; i < level; i++)
			buffer.append(' ');
		buffer.append(actor);
		buffer.append('\n');
		if (actor instanceof Group) {
			Array<Actor> actors = ((Group)actor).getActors();
			for (int i = 0, n = actors.size; i < n; i++)
				graphToString(buffer, actors.get(i), level + 1);
		}
	}

	/** Converts the coordinates given in the child's parent coordinate system to the child's coordinate system. */
	static public void toChildCoordinates (Actor child, float parentX, float parentY, Vector2 out) {
		float rotation = child.getRotation();
		float scaleX = child.getScaleX();
		float scaleY = child.getScaleY();
		float childX = child.getX();
		float childY = child.getY();

		if (rotation == 0) {
			if (scaleX == 1 && scaleY == 1) {
				out.x = parentX - childX;
				out.y = parentY - childY;
			} else {
				float originX = child.getOriginX();
				float originY = child.getOriginY();
				if (originX == 0 && originY == 0) {
					out.x = (parentX - childX) / scaleX;
					out.y = (parentY - childY) / scaleY;
				} else {
					out.x = (parentX - childX - originX) / scaleX + originX;
					out.y = (parentY - childY - originY) / scaleY + originY;
				}
			}
		} else {
			final float cos = (float)Math.cos(rotation * MathUtils.degreesToRadians);
			final float sin = (float)Math.sin(rotation * MathUtils.degreesToRadians);

			float originX = child.getOriginX();
			float originY = child.getOriginY();

			if (scaleX == 1 && scaleY == 1) {
				if (originX == 0 && originY == 0) {
					float tox = parentX - childX;
					float toy = parentY - childY;

					out.x = tox * cos + toy * sin;
					out.y = tox * -sin + toy * cos;
				} else {
					final float worldOriginX = childX + originX;
					final float worldOriginY = childY + originY;
					float fx = -originX;
					float fy = -originY;

					float x1 = cos * fx - sin * fy;
					float y1 = sin * fx + cos * fy;
					x1 += worldOriginX;
					y1 += worldOriginY;

					float tox = parentX - x1;
					float toy = parentY - y1;

					out.x = tox * cos + toy * sin;
					out.y = tox * -sin + toy * cos;
				}
			} else {
				if (originX == 0 && originY == 0) {
					float tox = parentX - childX;
					float toy = parentY - childY;

					out.x = tox * cos + toy * sin;
					out.y = tox * -sin + toy * cos;

					out.x /= scaleX;
					out.y /= scaleY;
				} else {
					final float worldOriginX = childX + originX;
					final float worldOriginY = childY + originY;
					float fx = -originX * scaleX;
					float fy = -originY * scaleY;

					float x1 = cos * fx - sin * fy;
					float y1 = sin * fx + cos * fy;
					x1 += worldOriginX;
					y1 += worldOriginY;

					float tox = parentX - x1;
					float toy = parentY - y1;

					out.x = tox * cos + toy * sin;
					out.y = tox * -sin + toy * cos;

					out.x /= scaleX;
					out.y /= scaleY;
				}
			}
		}
	}
}
