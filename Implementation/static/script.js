document.addEventListener("DOMContentLoaded", function() {

    // role stored in localStorage so it stays after refresh
    var savedRole = localStorage.getItem('role');
    var currentRole = savedRole ? savedRole : 'GUEST';

    function saveRole(r) {
        currentRole = r;
        localStorage.setItem('role', r);
        updateUI();
    }

    // show/hide things depending on role
    function updateUI() {
        var isGuest = currentRole === 'GUEST';
        var isAuth  = currentRole === 'AUTH';
        var isAdmin = currentRole === 'ADMIN';

        // AUTH and ADMIN can see auth-only elements
        var authElements = document.querySelectorAll('[data-min-role="AUTH"]');
        for (var i = 0; i < authElements.length; i++) {
            if (isGuest) {
                authElements[i].style.display = 'none';
            } else {
                authElements[i].style.display = '';
            }
        }

        // only ADMIN sees admin-only elements
        var adminElements = document.querySelectorAll('[data-min-role="ADMIN"]');
        for (var j = 0; j < adminElements.length; j++) {
            if (isAdmin) {
                adminElements[j].style.display = '';
            } else {
                adminElements[j].style.display = 'none';
            }
        }

        // detail page: show/hide Edit+Delete based on role + ownership
        var editBtn   = document.getElementById('detailEditBtn');
        var deleteForm = document.getElementById('detailDeleteForm');
        if (editBtn || deleteForm) {
            var bodyEl = document.body;
            var entityType = bodyEl.getAttribute('data-entity-type') || '';
            var entityId   = parseInt(bodyEl.getAttribute('data-entity-id') || '0', 10);

            var canSeeControls = false;
            if (isAdmin) {
                canSeeControls = true;
            } else if (isAuth) {
                // check if this item was created by this "user" (localStorage)
                var owned = JSON.parse(localStorage.getItem('ownedItems') || '[]');
                for (var k = 0; k < owned.length; k++) {
                    if (owned[k].type === entityType && owned[k].id === entityId) {
                        canSeeControls = true;
                        break;
                    }
                }
            }
            if (editBtn)    editBtn.style.display    = canSeeControls ? '' : 'none';
            if (deleteForm) deleteForm.style.display = canSeeControls ? '' : 'none';
        }

        // update badge color/text
        var badge = document.getElementById('roleBadge');
        if (badge) {
            badge.textContent = currentRole;
            badge.className = 'role-badge';
            if (currentRole === 'GUEST') badge.classList.add('role-guest');
            if (currentRole === 'AUTH') badge.classList.add('role-auth');
            if (currentRole === 'ADMIN') badge.classList.add('role-admin');
        }

        // update dropdown to match
        var dd = document.getElementById('roleDropdown');
        if (dd) dd.value = currentRole;

        // update hint text
        var hint = document.getElementById('roleHint');
        if (hint) {
            if (currentRole === 'GUEST') hint.textContent = 'View only - no edits allowed';
            else if (currentRole === 'AUTH') hint.textContent = 'Can create and edit artworks, ratings, stars';
            else if (currentRole === 'ADMIN') hint.textContent = 'Full access + manage roles and users';
        }
    }


    // loading screen while we fetch sidebar data
    var loader = document.createElement("div");
    loader.id = "loading-screen";
    loader.innerHTML = "<h1>JavaAutumn</h1><p>Loading...</p>";
    document.body.appendChild(loader);

    fetch('/api/sidebar')
        .then(function(res) { return res.json(); })
        .then(function(data) {

            var originalChildren = Array.from(document.body.childNodes).filter(function(n) {
                return n.id !== 'loading-screen';
            });
            originalChildren.forEach(function(node) {
                document.body.removeChild(node);
            });

            var layout = document.createElement('div');
            layout.className = 'app-layout';

            var sidebar = document.createElement('div');
            sidebar.className = 'app-sidebar';

            // role switcher panel at top of sidebar
            var rolePanel = document.createElement('div');
            rolePanel.className = 'role-panel';
            rolePanel.innerHTML =
                '<div class="role-panel-label">Active User</div>' +
                '<div class="role-select-row">' +
                    '<span id="roleBadge" class="role-badge"></span>' +
                    '<select id="roleDropdown" class="role-dropdown">' +
                        '<option value="GUEST">GUEST</option>' +
                        '<option value="AUTH">AUTH</option>' +
                        '<option value="ADMIN">ADMIN</option>' +
                    '</select>' +
                '</div>' +
                '<div class="role-hint" id="roleHint"></div>';
            sidebar.appendChild(rolePanel);

            // accordion sections - limit 10 items each
            var MAX_ITEMS = 10;

            var sections = [
                { title: 'Artist',     items: data.artists    || [], linkBase: '/artist?id=',     addable: true,  createUrl: '/artists/create-sidebar'          },
                { title: 'Artwork',    items: data.artworks   || [], linkBase: '/artwork?id=',    addable: true,  createUrl: '/artworks/create-sidebar'         },
                { title: 'Provenance', items: data.provenances|| [], linkBase: '/provenance?id=', addable: true,  createUrl: '/provenances/create-sidebar'       },
                { title: 'Epoch',      items: data.epochs     || [], linkBase: '/epoch?id='     },
                { title: 'User',       items: data.users      || [], linkBase: '/user?id=', labelKey: 'name' },
                { title: 'Rating',     items: data.ratings    || [], linkBase: '/rating?id=', listUrl: '/ratings' },
                { title: 'Stars',      items: data.stars      || [], linkBase: '/stars?id='     },
                { title: 'Role',       items: data.roles      || [], linkBase: '/role?id='      },
            ];

            // helper: build form fields HTML for each addable section
            function buildFormHtml(title, artistList, provenanceList) {
                if (title === 'Artist') {
                    return '<input name="displayedAs" placeholder="Short name (e.g. Botticelli)" required style="width:100%;margin-bottom:5px;">'
                         + '<input name="fullName" placeholder="Full name" style="width:100%;margin-bottom:5px;">'
                         + '<input name="birthDate" placeholder="Birth date (e.g. 01.03.1445)" style="width:100%;margin-bottom:5px;">'
                         + '<input name="deathDate" placeholder="Death date" style="width:100%;margin-bottom:5px;">'
                         + '<input name="bioUrl" placeholder="Wikipedia / bio URL" style="width:100%;margin-bottom:5px;">'
                         + '<input name="pictureUrl" placeholder="Picture URL" style="width:100%;margin-bottom:5px;">';
                }
                if (title === 'Artwork') {
                    var artistOpts = '<option value="">Select Artist...</option>';
                    artistList.forEach(function(a) { artistOpts += '<option value="' + a.id + '">' + (a.displayedAs || a.id) + '</option>'; });
                    var provOpts = '<option value="">Select Provenance...</option>';
                    provenanceList.forEach(function(p) { provOpts += '<option value="' + p.id + '">' + (p.displayedAs || p.id) + '</option>'; });
                    return '<input name="displayedAs" placeholder="Artwork title" required style="width:100%;margin-bottom:5px;">'
                         + '<input name="material" placeholder="Material (e.g. Oil on canvas)" style="width:100%;margin-bottom:5px;">'
                         + '<input name="pictureUrl" placeholder="Picture URL (optional)" style="width:100%;margin-bottom:5px;">'
                         + '<select name="artistId" required style="width:100%;margin-bottom:5px;">' + artistOpts + '</select>'
                         + '<select name="provenanceId" required style="width:100%;margin-bottom:5px;">' + provOpts + '</select>';
                }
                if (title === 'Provenance') {
                    return '<input name="displayedAs" placeholder="Museum / gallery name" required style="width:100%;margin-bottom:5px;">';
                }
                return '';
            }

            // keep refs so we can close others when one opens
            var accordionSections = [];

            sections.forEach(function(sec) {
                if (sec.items.length === 0 && !sec.addable && !sec.listUrl) return;

                var sectionDiv = document.createElement('div');
                sectionDiv.className = 'sidebar-section';

                // title row: ▶ TITLE   [＋] button for addable sections
                var titleEl = document.createElement('div');
                titleEl.className = 'sidebar-title';
                titleEl.style.display = 'flex';
                titleEl.style.justifyContent = 'space-between';
                titleEl.style.alignItems = 'center';

                var titleText = document.createElement('span');
                titleText.textContent = '▶ ' + sec.title.toUpperCase();
                titleEl.appendChild(titleText);

                var itemsContainer = document.createElement('div');
                itemsContainer.style.display = 'none';

                // add ＋ button for addable sections (AUTH only)
                if (sec.addable) {
                    var addBtn = document.createElement('button');
                    addBtn.textContent = '＋';
                    addBtn.title = 'Add new ' + sec.title;
                    addBtn.className = 'sidebar-add-btn';
                    addBtn.setAttribute('data-min-role', 'AUTH');
                    addBtn.addEventListener('click', function(e) {
                        e.stopPropagation();
                        var existing = sectionDiv.querySelector('.sidebar-inline-form');
                        if (existing) {
                            existing.style.display = existing.style.display === 'none' ? 'block' : 'none';
                            return;
                        }
                        // build the inline form
                        var formWrap = document.createElement('div');
                        formWrap.className = 'sidebar-inline-form';
                        formWrap.innerHTML = buildFormHtml(sec.title, data.artists || [], data.provenances || [])
                            + '<button type="button" class="sidebar-inline-submit">Save</button>';
                        sectionDiv.appendChild(formWrap);

                        formWrap.querySelector('.sidebar-inline-submit').addEventListener('click', function() {
                            var inputs = formWrap.querySelectorAll('input, select');
                            var body = '';
                            inputs.forEach(function(inp) {
                                if (body) body += '&';
                                body += encodeURIComponent(inp.name) + '=' + encodeURIComponent(inp.value);
                            });
                            fetch(sec.createUrl, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: body
                            }).then(function(res) {
                                if (res.ok) {
                                    res.text().then(function(newId) {
                                        // save ownership so AUTH can edit/delete this item later
                                        var ownedItems = JSON.parse(localStorage.getItem('ownedItems') || '[]');
                                        ownedItems.push({ type: sec.title, id: parseInt(newId, 10) });
                                        localStorage.setItem('ownedItems', JSON.stringify(ownedItems));
                                        formWrap.innerHTML = '<span style="color:#4CAF50;font-size:0.85rem;">✓ Saved! Refreshing...</span>';
                                        setTimeout(function() { window.location.reload(); }, 800);
                                    });
                                } else {
                                    res.text().then(function(t) {
                                        formWrap.innerHTML += '<span style="color:#f44;font-size:0.8rem;"> Error: ' + t + '</span>';
                                    });
                                }
                            });
                        });
                    });
                    titleEl.appendChild(addBtn);
                }

                if (sec.listUrl) {
                    var listLink = document.createElement('a');
                    listLink.className = 'sidebar-item';
                    listLink.href = sec.listUrl;
                    listLink.textContent = 'View all ' + sec.listUrl.split('/').pop() + ' →';
                    itemsContainer.appendChild(listLink);
                }

                var visible = sec.items.slice(0, MAX_ITEMS);
                var extra = sec.items.length - visible.length;

                visible.forEach(function(item) {
                    var label = item[sec.labelKey || 'displayedAs'] || 'Unknown';
                    var link = document.createElement('a');
                    link.className = 'sidebar-item';
                    link.href = sec.linkBase + item.id;
                    link.textContent = label;
                    itemsContainer.appendChild(link);
                });

                if (extra > 0) {
                    var moreEl = document.createElement('div');
                    moreEl.className = 'sidebar-more';
                    moreEl.textContent = '... ve ' + extra + ' tane daha';
                    itemsContainer.appendChild(moreEl);
                }

                titleEl.addEventListener('click', function(e) {
                    if (e.target.classList.contains('sidebar-add-btn')) return;
                    var open = itemsContainer.style.display !== 'none';
                    accordionSections.forEach(function(s) {
                        s.items.style.display = 'none';
                        s.title.querySelector('span').textContent = s.title.querySelector('span').textContent.replace('▼', '▶');
                    });
                    if (!open) {
                        itemsContainer.style.display = 'block';
                        titleText.textContent = titleText.textContent.replace('▶', '▼');
                    }
                });

                accordionSections.push({ items: itemsContainer, title: titleEl });

                sectionDiv.appendChild(titleEl);
                sectionDiv.appendChild(itemsContainer);
                sidebar.appendChild(sectionDiv);
            });

            var mainContent = document.createElement('div');
            mainContent.className = 'app-main';
            originalChildren.forEach(function(node) {
                mainContent.appendChild(node);
            });

            // footer contact form (fake, just for looks)
            var footer = document.createElement('div');
            footer.innerHTML =
                '<div style="margin-top:60px;padding:40px 20px;border-top:1px solid var(--border-color);background:rgba(0,0,0,0.2);border-radius:12px 12px 0 0;">' +
                    '<div style="max-width:500px;margin:0 auto;text-align:center;">' +
                        '<h3 style="color:var(--accent-gold);margin-bottom:10px;font-size:1.5rem;">Get in Touch</h3>' +
                        '<p style="color:var(--text-muted);font-size:0.95rem;margin-bottom:25px;">Have questions about the gallery? Send us a message.</p>' +
                        '<form id="fakeContactForm" style="display:flex;flex-direction:column;gap:15px;text-align:left;">' +
                            '<input type="text" placeholder="Your Name" required style="width:100%;">' +
                            '<input type="email" placeholder="Your Email" required style="width:100%;">' +
                            '<textarea placeholder="Your Message" rows="4" required style="width:100%;resize:vertical;"></textarea>' +
                            '<button type="submit" style="background:linear-gradient(135deg,var(--accent-orange),var(--accent-gold));color:white;border:none;padding:14px;border-radius:8px;cursor:pointer;font-weight:bold;font-size:1rem;transition:0.3s;margin-top:5px;">Send Message ↗</button>' +
                        '</form>' +
                        '<div id="contactSuccessMsg" style="display:none;margin-top:25px;padding:20px;background:rgba(76,175,80,0.15);border:1px solid #4CAF50;border-radius:8px;color:#4CAF50;text-align:center;">' +
                            '<div style="font-size:2rem;margin-bottom:10px;">✅</div>' +
                            '<strong style="font-size:1.1rem;">Message Sent Successfully!</strong><br>' +
                            '<span style="font-size:0.9rem;opacity:0.8;display:block;margin-top:5px;">Thank you for reaching out. Our curators will get back to you soon.</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            mainContent.appendChild(footer);

            // fake form submit handler
            setTimeout(function() {
                var form = document.getElementById('fakeContactForm');
                var successMsg = document.getElementById('contactSuccessMsg');
                if (form) {
                    form.addEventListener('submit', function(e) {
                        e.preventDefault();
                        var btn = form.querySelector('button');
                        btn.innerHTML = "Sending... ⏳";
                        btn.style.opacity = "0.7";
                        btn.disabled = true;
                        setTimeout(function() {
                            form.style.display = 'none';
                            successMsg.style.display = 'block';
                        }, 1200); // simulate network delay
                    });
                }
            }, 0);

            layout.appendChild(sidebar);
            layout.appendChild(mainContent);
            document.body.appendChild(layout);

            // wire up the role dropdown
            var roleDropdown = document.getElementById('roleDropdown');
            if (roleDropdown) {
                roleDropdown.addEventListener('change', function(e) {
                    saveRole(e.target.value);
                });
            }

            // apply saved role on load
            updateUI();

            loader.classList.add("fade-out");
            setTimeout(function() { loader.remove(); }, 800);
        })
        .catch(function(err) {
            console.error('Sidebar load failed:', err);
            loader.classList.add("fade-out");
            setTimeout(function() { loader.remove(); }, 800);
        });

    // falling leaves
    var leafColors = [
        ["#d95d39", "#a03020"],
        ["#f0a202", "#c07800"],
        ["#8b2020", "#5a1010"],
        ["#c0640a", "#7a3a00"],
        ["#b8860b", "#7a5700"],
        ["#e07030", "#a04010"],
    ];

    function makeLeafSVG(fill, vein) {
        return '<svg viewBox="0 0 100 120" xmlns="http://www.w3.org/2000/svg">'
            + '<path d="M 50,5 C 15,30 15,85 50,115 C 85,85 85,30 50,5 Z" fill="' + fill + '" opacity="0.88"/>'
            + '<line x1="50" y1="8" x2="50" y2="112" stroke="' + vein + '" stroke-width="1.8" stroke-linecap="round" opacity="0.7"/>'
            + '<line x1="50" y1="30" x2="22" y2="48" stroke="' + vein + '" stroke-width="1.1" stroke-linecap="round" opacity="0.6"/>'
            + '<line x1="50" y1="45" x2="18" y2="60" stroke="' + vein + '" stroke-width="1.0" stroke-linecap="round" opacity="0.55"/>'
            + '<line x1="50" y1="58" x2="20" y2="72" stroke="' + vein + '" stroke-width="0.9" stroke-linecap="round" opacity="0.5"/>'
            + '<line x1="50" y1="72" x2="24" y2="85" stroke="' + vein + '" stroke-width="0.8" stroke-linecap="round" opacity="0.45"/>'
            + '<line x1="50" y1="30" x2="78" y2="48" stroke="' + vein + '" stroke-width="1.1" stroke-linecap="round" opacity="0.6"/>'
            + '<line x1="50" y1="45" x2="82" y2="60" stroke="' + vein + '" stroke-width="1.0" stroke-linecap="round" opacity="0.55"/>'
            + '<line x1="50" y1="58" x2="80" y2="72" stroke="' + vein + '" stroke-width="0.9" stroke-linecap="round" opacity="0.5"/>'
            + '<line x1="50" y1="72" x2="76" y2="85" stroke="' + vein + '" stroke-width="0.8" stroke-linecap="round" opacity="0.45"/>'
            + '<line x1="50" y1="112" x2="50" y2="120" stroke="' + vein + '" stroke-width="2" stroke-linecap="round" opacity="0.7"/>'
            + '</svg>';
    }

    for (var k = 0; k < 18; k++) {
        var leaf = document.createElement("div");
        leaf.classList.add("leaf");
        var colors = leafColors[k % leafColors.length];
        leaf.innerHTML = makeLeafSVG(colors[0], colors[1]);
        leaf.style.left = (Math.random() * 100) + '%';
        leaf.style.animationDelay = (Math.random() * 8) + 's';
        leaf.style.animationDuration = (7 + Math.random() * 8) + 's';
        document.body.appendChild(leaf);
    }
});
