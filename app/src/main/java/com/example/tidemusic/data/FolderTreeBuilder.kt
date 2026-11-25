package com.example.tidemusic.data

import com.example.tidemusic.data.db.FolderEntity
import com.example.tidemusic.data.db.SongEntity
import com.example.tidemusic.data.db.StableIds

/**
 * Folder scanning algorithm (spec Section 8.5).
 *
 *  1. Build the path tree from every song's file path.
 *  2. Collapse single-child intermediate directories that have no audio of their own,
 *     so the top-level list shown is the smallest set of "real" music roots.
 *  3. Precompute recursive (songCount, subfolderCount, totalDurationMs) for every node
 *     so the Folder screen header renders instantly without recompute per navigation.
 *  4. Respect the user's include/exclude folder list when building the tree.
 *
 * Implementation notes: this is a pure function of the input songs — no I/O, no coroutine,
 * no Android imports beyond the data classes — so it is easy to verify and reason about.
 * All mutation happens in the local [Node] tree and is discarded once [buildEntities] returns.
 */
class FolderTreeBuilder {

    /** Output of a build: the flat list of folder entities (parents/children linked by id). */
    data class Tree(val folders: List<FolderEntity>, val rootIds: List<Long>) {
        fun roots(): List<FolderEntity> = folders.filter { it.id in rootIds }
        fun byId(id: Long): FolderEntity? = folders.firstOrNull { it.id == id }
    }

    fun build(
        songs: List<SongEntity>,
        includePrefixes: Collection<String>? = null,
    ): Tree {
        val filtered = songs.filter { song ->
            includePrefixes == null || includePrefixes.any { prefix ->
                val p = prefix.trimEnd('/')
                song.filePath.startsWith("$p/") || song.filePath == p
            }
        }
        if (filtered.isEmpty()) return Tree(emptyList(), emptyList())

        // 1. Node tree keyed by absolute path, parents linked via reference.
        val nodes = LinkedHashMap<String, Node>()
        for (song in filtered) {
            val leafPath = song.filePath.substringBeforeLast('/', "")
            if (leafPath.isBlank()) continue
            ensurePath(nodes, leafPath)
            nodes[leafPath]!!.directSongs.add(song)
        }
        if (nodes.isEmpty()) return Tree(emptyList(), emptyList())

        // 2. Collapse: drop a node if it has exactly zero direct songs and a single child,
        //    rewiring that child to the dropped node's parent. Iterate to a fixpoint. The
        //    child keeps its own (longer) path so the user still sees the real folder name.
        var changed = true
        while (changed) {
            changed = false
            val snapshot = nodes.values.toList()
            for (node in snapshot) {
                if (node.directSongs.isNotEmpty()) continue
                val children = nodes.values.filter { it.parent === node }
                if (children.size != 1) continue
                val child = children.single()
                // Rewire child's parent to this node's parent; remove this node.
                child.parent = node.parent
                nodes.remove(node.path)
                changed = true
                break
            }
        }

        // 2.5. Flatten single meaningless roots: if there is only one root and it has no direct songs,
        // promote its children to be roots. Repeat until we have multiple roots or songs.
        var currentRoots = nodes.values.filter { it.parent == null }
        while (currentRoots.size == 1) {
            val root = currentRoots.first()
            if (root.directSongs.isNotEmpty()) break
            
            val children = nodes.values.filter { it.parent === root }
            if (children.isEmpty()) break
            
            nodes.remove(root.path)
            for (child in children) {
                child.parent = null
            }
            currentRoots = children
        }


        // 3. Recursive aggregate pass. Because children always have a strictly longer path
        //    than their parent after collapse, walking by ascending path length visits every
        //    parent after its descendants — so we can fold aggregates bottom-up in one pass.
        val byPathAsc = nodes.values.sortedBy { it.path.length }
        for (node in byPathAsc) {
            node.recursiveSongCount = node.directSongs.size
            node.recursiveSubfolderCount = 0
            node.recursiveDurationMs = node.directSongs.sumOf { it.durationMs }
        }
        val byPathDesc = nodes.values.sortedByDescending { it.path.length }
        for (node in byPathDesc) {
            val parent = node.parent ?: continue
            parent.recursiveSongCount += node.recursiveSongCount
            parent.recursiveSubfolderCount += 1 + node.recursiveSubfolderCount
            parent.recursiveDurationMs += node.recursiveDurationMs
        }

        // 4. Emit FolderEntity rows. Only nodes that survived collapse are emitted — every
        //    parent reference is still valid because we rewired children during collapse.
        val folderEntities = nodes.values.map { n ->
            FolderEntity(
                id = StableIds.folderId(n.path),
                path = n.path,
                parentFolderId = n.parent?.let { StableIds.folderId(it.path) },
                depth = n.depth(),
                songCount = n.recursiveSongCount,
                subfolderCount = n.recursiveSubfolderCount,
                totalDurationMs = n.recursiveDurationMs,
            )
        }
        // After collapse some nodes may reference a parent that was removed from the
        // nodes map. Null out those orphan parentFolderIds so the DAO query
        // "WHERE parent_folder_id IS NULL" correctly finds them as tree roots.
        val emittedIds = folderEntities.map { it.id }.toSet()
        val fixedEntities = folderEntities.map { entity ->
            if (entity.parentFolderId != null && entity.parentFolderId !in emittedIds) {
                entity.copy(parentFolderId = null)
            } else {
                entity
            }
        }
        val rootIds = fixedEntities
            .filter { it.parentFolderId == null }
            .map { it.id }
        return Tree(fixedEntities.sortedBy { it.path }, rootIds)
    }

    /** Create [nodes] for every intermediate directory in [path] along with the leaf. */
    private fun ensurePath(nodes: LinkedHashMap<String, Node>, path: String) {
        if (nodes.containsKey(path)) return
        val toCreate = ArrayList<String>()
        var cur = path
        while (!nodes.containsKey(cur) && cur.isNotBlank()) {
            toCreate.add(0, cur)
            val parent = cur.substringBeforeLast('/', "")
            if (parent == cur || parent.isBlank()) break
            cur = parent
        }
        var parent: Node? = nodes[cur]
        for (seg in toCreate) {
            val n = Node(seg)
            n.parent = parent
            nodes[seg] = n
            parent = n
        }
    }

    /** Mutable tree node; alive only for the duration of [build]. */
    private class Node(val path: String) {
        var parent: Node? = null
        val directSongs: MutableList<SongEntity> = mutableListOf()
        var recursiveSongCount: Int = 0
        var recursiveSubfolderCount: Int = 0
        var recursiveDurationMs: Long = 0L

        /** Number of nested directory segments in the path (0 = top-level root). */
        fun depth(): Int = path.split('/').filter { it.isNotBlank() }.size - 1
    }
}
