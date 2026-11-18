/*
 * This file is part of OpenSourceFinder.
 *
 * Copyright (C) 2026 deepcodecreate
 *
 * OpenSourceFinder is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenSourceFinder is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenSourceFinder. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.opensourcefinder.app.glide;

import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.caverock.androidsvg.SVG;

public final class SvgDrawableTranscoder implements ResourceTranscoder<SVG, PictureDrawable> {

    private static final float FALLBACK_SIZE = 512f;

    @Nullable
    @Override
    public Resource<PictureDrawable> transcode(@NonNull Resource<SVG> toTranscode, @NonNull Options options) {
        SVG svg = toTranscode.get();

        if (svg.getDocumentWidth() < 0 || svg.getDocumentHeight() < 0) {
            RectF viewBox = svg.getDocumentViewBox();
            if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                svg.setDocumentViewBox(viewBox.left, viewBox.top, viewBox.width(), viewBox.height());
                svg.setDocumentWidth(viewBox.width());
                svg.setDocumentHeight(viewBox.height());
            } else {
                svg.setDocumentWidth(FALLBACK_SIZE);
                svg.setDocumentHeight(FALLBACK_SIZE);
            }
        }

        Picture picture = svg.renderToPicture();
        PictureDrawable drawable = new PictureDrawable(picture);
        return new SimpleResource<>(drawable);
    }
}
