document.addEventListener("DOMContentLoaded", () => {
    // 1. Loading Screen & Sidebar Layout Setup
    const loader = document.createElement("div");
    loader.id = "loading-screen";
    loader.innerHTML = "<h1>JavaAutumn</h1><p>Loading...</p>";
    document.body.appendChild(loader);

    fetch('/api/sidebar')
        .then(res => res.json())
        .then(data => {
            // Save original body and clear it (keep loader temporarily)
            const originalChildren = Array.from(document.body.childNodes).filter(node => node.id !== 'loading-screen');
            originalChildren.forEach(node => document.body.removeChild(node));

            // Create layout
            const layout = document.createElement('div');
            layout.className = 'app-layout';

            // Create sidebar
            const sidebar = document.createElement('div');
            sidebar.className = 'app-sidebar';
            
            // Generate accordion sections
            const sections = [
                { title: 'Artist', items: data.artists || [], linkBase: '/artist?id=' },
                { title: 'Artwork', items: data.artworks || [], linkBase: '/artwork?id=' },
                { title: 'Provenance', items: data.provenances || [], linkBase: '/provenance?id=' },
                { title: 'Epoch', items: data.epochs || [], linkBase: '/epoch?id=' },
                { title: 'User', items: data.users || [], linkBase: '/user?id=', labelKey: 'name' },
                { title: 'Role', items: data.roles || [], linkBase: '/role?id=' },
                { title: 'Rating', items: data.ratings || [], linkBase: '/rating?id=' },
                { title: 'Stars', items: data.stars || [], linkBase: '/stars?id=' }
            ];

            // 1. Static Navigation Links
            const navSection = document.createElement('div');
            navSection.className = 'sidebar-section';
            navSection.innerHTML = `
                <div class="sidebar-title">Navigation</div>
                <a class="sidebar-item" href="/">🏠 Home</a>
                <a class="sidebar-item" href="/users">👤 Users</a>
                <a class="sidebar-item" href="/artworks">🎨 Artworks</a>
                <a class="sidebar-item" href="/ratings">⭐ Ratings</a>
                <a class="sidebar-item" href="/stars">✨ Stars</a>
                <a class="sidebar-item" href="/roles">🔑 Roles</a>
            `;
            sidebar.appendChild(navSection);

            // 2. Entity Links (Dynamic)
            sections.forEach(sec => {
                if (sec.items.length === 0) return;
                const sectionDiv = document.createElement('div');
                sectionDiv.className = 'sidebar-section';
                
                const title = document.createElement('div');
                title.className = 'sidebar-title';
                title.innerHTML = `▼ ${sec.title}`;
                
                const itemsContainer = document.createElement('div');
                sec.items.forEach(item => {
                    const label = item[sec.labelKey || 'displayedAs'] || 'Unknown';
                    const link = document.createElement('a');
                    link.className = 'sidebar-item';
                    link.href = `${sec.linkBase}${item.id}`;
                    link.textContent = label;
                    itemsContainer.appendChild(link);
                });
                
                // Toggle accordion
                title.onclick = () => {
                    itemsContainer.style.display = itemsContainer.style.display === 'none' ? 'block' : 'none';
                    title.innerHTML = itemsContainer.style.display === 'none' ? `▶ ${sec.title}` : `▼ ${sec.title}`;
                };
                
                sectionDiv.appendChild(title);
                sectionDiv.appendChild(itemsContainer);
                sidebar.appendChild(sectionDiv);
            });

            // Create main content area
            const mainContent = document.createElement('div');
            mainContent.className = 'app-main';
            originalChildren.forEach(node => mainContent.appendChild(node));

            // Footer Contact Form
            const footer = document.createElement('div');
            footer.innerHTML = `
                <div style="margin-top: 60px; padding: 40px 20px; border-top: 1px solid var(--border-color); background: rgba(0, 0, 0, 0.2); border-radius: 12px 12px 0 0;">
                    <div style="max-width: 500px; margin: 0 auto; text-align: center;">
                        <h3 style="color: var(--accent-gold); margin-bottom: 10px; font-size: 1.5rem;">Get in Touch</h3>
                        <p style="color: var(--text-muted); font-size: 0.95rem; margin-bottom: 25px;">Have questions about the gallery? Send us a message.</p>
                        
                        <form id="fakeContactForm" style="display: flex; flex-direction: column; gap: 15px; text-align: left;">
                            <input type="text" placeholder="Your Name" required style="width: 100%;">
                            <input type="email" placeholder="Your Email" required style="width: 100%;">
                            <textarea placeholder="Your Message" rows="4" required style="width: 100%; resize: vertical;"></textarea>
                            <button type="submit" style="background: linear-gradient(135deg, var(--accent-orange), var(--accent-gold)); color: white; border: none; padding: 14px; border-radius: 8px; cursor: pointer; font-weight: bold; font-size: 1rem; transition: 0.3s; margin-top: 5px;">Send Message ↗</button>
                        </form>

                        <div id="contactSuccessMsg" style="display: none; margin-top: 25px; padding: 20px; background: rgba(76, 175, 80, 0.15); border: 1px solid #4CAF50; border-radius: 8px; color: #4CAF50; text-align: center;">
                            <div style="font-size: 2rem; margin-bottom: 10px;">✅</div>
                            <strong style="font-size: 1.1rem;">Message Sent Successfully!</strong><br>
                            <span style="font-size: 0.9rem; opacity: 0.8; display: block; margin-top: 5px;">Thank you for reaching out. Our curators will get back to you soon.</span>
                        </div>
                    </div>
                </div>
            `;
            mainContent.appendChild(footer);

            // Add fake contact form logic
            setTimeout(() => {
                const form = document.getElementById('fakeContactForm');
                const successMsg = document.getElementById('contactSuccessMsg');
                if (form) {
                    form.addEventListener('submit', (e) => {
                        e.preventDefault();
                        const btn = form.querySelector('button');
                        btn.innerHTML = "Sending... ⏳";
                        btn.style.opacity = "0.7";
                        btn.disabled = true;
                        
                        setTimeout(() => {
                            form.style.display = 'none';
                            successMsg.style.display = 'block';
                        }, 1200); // simulate network delay
                    });
                }
            }, 0);

            // Assemble layout
            layout.appendChild(sidebar);
            layout.appendChild(mainContent);
            document.body.appendChild(layout);

            // Hide loader
            loader.classList.add("fade-out");
            setTimeout(() => loader.remove(), 800);
        })
        .catch(err => {
            console.error('Sidebar load failed:', err);
            loader.classList.add("fade-out");
            setTimeout(() => loader.remove(), 800);
        });

    // 2. Falling Leaves Logic — SVG detailed leaves
    const leafColors = [
        // [fill, vein]
        ["#d95d39", "#a03020"],   // orange
        ["#f0a202", "#c07800"],   // gold
        ["#8b2020", "#5a1010"],   // deep red
        ["#c0640a", "#7a3a00"],   // brown-orange
        ["#b8860b", "#7a5700"],   // dark goldenrod
        ["#e07030", "#a04010"],   // amber
    ];

    // Detailed maple-style leaf SVG paths
    function createLeafSVG(fillColor, veinColor) {
        return `<svg viewBox="0 0 100 120" xmlns="http://www.w3.org/2000/svg">
  <!-- Leaf body -->
  <path d="
    M 50, 5
    C 15,30 15,85 50,115
    C 85,85 85,30 50,5
    Z
  " fill="${fillColor}" opacity="0.88"/>
  <!-- Main vein (midrib) -->
  <line x1="50" y1="8" x2="50" y2="112" stroke="${veinColor}" stroke-width="1.8" stroke-linecap="round" opacity="0.7"/>
  <!-- Left veins -->
  <line x1="50" y1="30" x2="22" y2="48" stroke="${veinColor}" stroke-width="1.1" stroke-linecap="round" opacity="0.6"/>
  <line x1="50" y1="45" x2="18" y2="60" stroke="${veinColor}" stroke-width="1.0" stroke-linecap="round" opacity="0.55"/>
  <line x1="50" y1="58" x2="20" y2="72" stroke="${veinColor}" stroke-width="0.9" stroke-linecap="round" opacity="0.5"/>
  <line x1="50" y1="72" x2="24" y2="85" stroke="${veinColor}" stroke-width="0.8" stroke-linecap="round" opacity="0.45"/>
  <!-- Right veins -->
  <line x1="50" y1="30" x2="78" y2="48" stroke="${veinColor}" stroke-width="1.1" stroke-linecap="round" opacity="0.6"/>
  <line x1="50" y1="45" x2="82" y2="60" stroke="${veinColor}" stroke-width="1.0" stroke-linecap="round" opacity="0.55"/>
  <line x1="50" y1="58" x2="80" y2="72" stroke="${veinColor}" stroke-width="0.9" stroke-linecap="round" opacity="0.5"/>
  <line x1="50" y1="72" x2="76" y2="85" stroke="${veinColor}" stroke-width="0.8" stroke-linecap="round" opacity="0.45"/>
  <!-- Stem -->
  <line x1="50" y1="112" x2="50" y2="120" stroke="${veinColor}" stroke-width="2" stroke-linecap="round" opacity="0.7"/>
</svg>`;
    }

    const leafCount = 18;
    for (let i = 0; i < leafCount; i++) {
        const leaf = document.createElement("div");
        leaf.classList.add("leaf");

        const colorPair = leafColors[i % leafColors.length];
        leaf.innerHTML = createLeafSVG(colorPair[0], colorPair[1]);

        const leftPos  = Math.random() * 100;
        const delay    = Math.random() * 8;
        const duration = 7 + Math.random() * 8;

        leaf.style.left = `${leftPos}%`;
        leaf.style.animationDelay = `${delay}s`;
        leaf.style.animationDuration = `${duration}s`;

        document.body.appendChild(leaf);
    }
});
