// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

/** Retail sidecar data uses the shared deterministic JSON encoder. */
final class ProjectJson {
    private ProjectJson(){}
    static String encode(Object value){return net.battlespades.world.JsonData.encode(value);}
}
