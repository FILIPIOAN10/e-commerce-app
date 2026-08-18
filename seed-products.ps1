$baseUrl = "http://localhost:8080"
$session = $null
$jwtToken = $null

# Helper: Get fresh CSRF token from session cookies
function Get-CsrfToken {
    $cookie = $script:session.Cookies.GetCookies($script:baseUrl) | Where-Object { $_.Name -eq "XSRF-TOKEN" }
    if ($cookie) { return $cookie.Value }
    # If no cookie yet, do a GET to trigger token generation
    Invoke-RestMethod -Uri "$($script:baseUrl)/api/public/products" -Method Get -WebSession $script:session | Out-Null
    $cookie = $script:session.Cookies.GetCookies($script:baseUrl) | Where-Object { $_.Name -eq "XSRF-TOKEN" }
    if ($cookie) { return $cookie.Value }
    return $null
}

# Helper: Get JWT from the HttpOnly springBootEcom cookie (path /api)
function Get-JwtToken {
    $apiUri = [Uri]"$($script:baseUrl)/api"
    $cookie = $script:session.Cookies.GetCookies($apiUri) | Where-Object { $_.Name -eq "springBootEcom" } | Select-Object -First 1
    if ($cookie) { return $cookie.Value }
    return $null
}

# Helper: Build headers with fresh CSRF token
function Get-AuthHeaders {
    $csrf = Get-CsrfToken
    return @{
        Authorization = "Bearer $($script:jwtToken)"
        "Content-Type" = "application/json"
        "X-XSRF-TOKEN" = $csrf
    }
}

# Helper: POST with automatic CSRF refresh + retry on 403
function Invoke-PostWithCsrf {
    param([string]$Uri, [string]$Body)
    $maxRetries = 2
    for ($attempt = 1; $attempt -le $maxRetries; $attempt++) {
        $h = Get-AuthHeaders
        try {
            return Invoke-RestMethod -Uri $Uri -Method Post -Body $Body -Headers $h -WebSession $script:session
        } catch {
            if ($_.Exception.Response.StatusCode -eq 403 -and $attempt -lt $maxRetries) {
                # CSRF token stale — do a GET to refresh, then retry
                Invoke-RestMethod -Uri "$($script:baseUrl)/api/public/products" -Method Get -WebSession $script:session | Out-Null
                Start-Sleep -Milliseconds 100
                continue
            }
            throw
        }
    }
}

# 1. Initialize session + get first CSRF token
Write-Host "=== Getting CSRF token ===" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$baseUrl/api/public/products" -Method Get -SessionVariable session | Out-Null
    $csrfToken = Get-CsrfToken
    Write-Host "Got CSRF token: $($csrfToken.Substring(0,10))..." -ForegroundColor Green
} catch {
    Write-Host "Failed to get CSRF token: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 2. Login as admin (auth endpoints are CSRF-exempt, but use session for cookies)
Write-Host "=== Logging in as admin ===" -ForegroundColor Cyan
$loginBody = @{ username = "admin"; password = "adminPass" } | ConvertTo-Json
try {
    $loginResp = Invoke-WebRequest -Uri "$baseUrl/api/auth/signin" -Method Post -Body $loginBody -ContentType "application/json" -WebSession $session -UseBasicParsing
    $setCookieHeader = $loginResp.Headers['Set-Cookie']
    if ($setCookieHeader -is [array]) { $setCookieHeader = $setCookieHeader | Where-Object { $_ -like 'springBootEcom=*' } | Select-Object -First 1 }
    if (-not $setCookieHeader -or $setCookieHeader -notlike 'springBootEcom=*') { Write-Host "No JWT cookie received. Check admin credentials or cookie settings." -ForegroundColor Red; exit 1 }
    $jwtToken = ($setCookieHeader -split ';')[0] -replace '^springBootEcom=', ''
    $script:jwtToken = $jwtToken
    if (-not $jwtToken) { Write-Host "No token received. Check admin credentials." -ForegroundColor Red; exit 1 }
    Write-Host "Got token: $($jwtToken.Substring(0,20))..." -ForegroundColor Green
} catch {
    Write-Host "Login failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Make sure you have an admin user. Sign up first, then manually set ROLE_ADMIN in DB." -ForegroundColor Yellow
    exit 1
}

# 3. Create 5 categories
Write-Host "`n=== Creating categories ===" -ForegroundColor Cyan
$categories = @("Electronics", "Clothing", "Home & Garden", "Sports", "Books")
$categoryIds = @{}

$existingCats = Invoke-RestMethod -Uri "$baseUrl/api/public/categories?pageNumber=0&pageSize=100" -Method Get
foreach ($cat in $categories) {
    $found = $existingCats.content | Where-Object { $_.categoryName -eq $cat } | Select-Object -First 1
    if ($found) {
        $categoryIds[$cat] = $found.categoryId
        Write-Host "  Already exists: $cat (ID=$($found.categoryId))" -ForegroundColor Yellow
        continue
    }
    $catBody = @{ categoryName = $cat } | ConvertTo-Json
    try {
        $resp = Invoke-PostWithCsrf -Uri "$baseUrl/api/admin/categories" -Body $catBody
        $categoryIds[$cat] = $resp.categoryId
        Write-Host "  Created: $cat (ID=$($resp.categoryId))" -ForegroundColor Green
    } catch {
        Write-Host "  Failed to create '$cat': $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 4. Product templates per category
$productTemplates = @{
    "Electronics" = @(
        @{ name = "Wireless Headphones"; desc = "Premium noise-cancelling Bluetooth headphones with 30h battery life"; tags = "audio,bluetooth,wireless" },
        @{ name = "Smart Watch"; desc = "Fitness tracking smartwatch with heart rate monitor and GPS"; tags = "wearable,fitness,smart" },
        @{ name = "USB-C Hub"; desc = "7-in-1 USB-C hub with HDMI, USB 3.0, and SD card reader"; tags = "usb,adapter,hub" },
        @{ name = "Bluetooth Speaker"; desc = "Portable waterproof speaker with deep bass and 20h playtime"; tags = "audio,bluetooth,portable" },
        @{ name = "Phone Charger"; desc = "Fast charging 65W GaN charger with USB-C and USB-A ports"; tags = "charger,usb,power" },
        @{ name = "Laptop Stand"; desc = "Aluminum adjustable laptop stand with heat dissipation"; tags = "laptop,stand,aluminum" },
        @{ name = "Wireless Mouse"; desc = "Ergonomic wireless mouse with silent clicks and 2.4GHz"; tags = "mouse,wireless,ergonomic" },
        @{ name = "Mechanical Keyboard"; desc = "RGB mechanical keyboard with hot-swappable switches"; tags = "keyboard,mechanical,rgb" },
        @{ name = "Webcam HD"; desc = "1080p HD webcam with built-in microphone and auto-focus"; tags = "webcam,camera,video" },
        @{ name = "Power Bank"; desc = "20000mAh portable power bank with fast charge and LED display"; tags = "power,battery,portable" },
        @{ name = "Earbuds Pro"; desc = "True wireless earbuds with ANC and wireless charging case"; tags = "audio,earbuds,wireless" },
        @{ name = "Tablet Stand"; desc = "Adjustable tablet stand with 360 degree rotation"; tags = "tablet,stand,adjustable" },
        @{ name = "HDMI Cable"; desc = "Premium 4K HDMI cable 2 meters with gold-plated connectors"; tags = "hdmi,cable,4k" },
        @{ name = "Smart Bulb"; desc = "WiFi smart LED bulb with color changing and app control"; tags = "smart,light,led" },
        @{ name = "Router WiFi 6"; desc = "Dual band WiFi 6 router with MU-MIMO and parental controls"; tags = "router,wifi,network" },
        @{ name = "External SSD"; desc = "1TB portable USB-C SSD with 1050MB/s read speed"; tags = "storage,ssd,usb" },
        @{ name = "Phone Case"; desc = "Shockproof phone case with magnetic kickstand"; tags = "phone,case,protective" },
        @{ name = "Desk Lamp LED"; desc = "Adjustable LED desk lamp with USB charging port"; tags = "lamp,led,desk" },
        @{ name = "Fitness Band"; desc = "Waterproof fitness tracker with sleep monitor and notifications"; tags = "fitness,tracker,wearable" },
        @{ name = "USB Flash Drive"; desc = "128GB USB 3.1 flash drive with metal casing"; tags = "usb,storage,flash" }
    )
    "Clothing" = @(
        @{ name = "Cotton T-Shirt"; desc = "100% organic cotton crew neck t-shirt in multiple colors"; tags = "shirt,cotton,casual" },
        @{ name = "Denim Jeans"; desc = "Slim fit stretch denim jeans with classic 5-pocket design"; tags = "jeans,denim,pants" },
        @{ name = "Hoodie Sweatshirt"; desc = "Fleece-lined pullover hoodie with kangaroo pocket"; tags = "hoodie,sweatshirt,warm" },
        @{ name = "Winter Jacket"; desc = "Waterproof insulated winter jacket with removable hood"; tags = "jacket,winter,warm" },
        @{ name = "Running Shoes"; desc = "Lightweight breathable running shoes with cushioned sole"; tags = "shoes,running,sports" },
        @{ name = "Leather Belt"; desc = "Genuine leather belt with brushed steel buckle"; tags = "belt,leather,accessory" },
        @{ name = "Wool Sweater"; desc = "Soft merino wool crew neck sweater for cold weather"; tags = "sweater,wool,warm" },
        @{ name = "Polo Shirt"; desc = "Classic pique cotton polo shirt with embroidered logo"; tags = "polo,shirt,casual" },
        @{ name = "Cargo Shorts"; desc = "Multi-pocket cargo shorts with elastic waistband"; tags = "shorts,cargo,summer" },
        @{ name = "Beanie Hat"; desc = "Knitted winter beanie hat with cuffed design"; tags = "hat,beanie,winter" },
        @{ name = "Dress Shirt"; desc = "Formal long-sleeve dress shirt with wrinkle-free fabric"; tags = "shirt,formal,dress" },
        @{ name = "Yoga Leggings"; desc = "High-waist opaque yoga leggings with hidden pocket"; tags = "leggings,yoga,fitness" },
        @{ name = "Rain Coat"; desc = "Lightweight waterproof rain coat with packable design"; tags = "coat,rain,waterproof" },
        @{ name = "Casual Sneakers"; desc = "Canvas low-top sneakers with rubber sole"; tags = "sneakers,casual,shoes" },
        @{ name = "Scarf Wool"; desc = "Soft wool blend scarf in classic plaid pattern"; tags = "scarf,wool,accessory" },
        @{ name = "Gloves Touch"; desc = "Touchscreen-compatible winter gloves with anti-slip grip"; tags = "gloves,winter,touchscreen" },
        @{ name = "Swim Trunks"; desc = "Quick-dry board shorts with mesh lining"; tags = "swim,trunks,summer" },
        @{ name = "Flannel Shirt"; desc = "Brushed flannel shirt with button-front and chest pockets"; tags = "flannel,shirt,casual" },
        @{ name = "Athletic Socks"; desc = "Moisture-wicking athletic socks pack of 6 pairs"; tags = "socks,athletic,sports" },
        @{ name = "Windbreaker"; desc = "Lightweight wind-resistant jacket with zip pockets"; tags = "jacket,windbreaker,lightweight" }
    )
    "Home & Garden" = @(
        @{ name = "Ceramic Mug Set"; desc = "Set of 4 stoneware coffee mugs in assorted colors"; tags = "mug,ceramic,kitchen" },
        @{ name = "Throw Blanket"; desc = "Soft fleece throw blanket for sofa and bed"; tags = "blanket,fleece,cozy" },
        @{ name = "Plant Pot Ceramic"; desc = "Modern ceramic planter with drainage saucer"; tags = "pot,plant,ceramic" },
        @{ name = "LED String Lights"; desc = "33ft warm white LED string lights with remote"; tags = "lights,led,decor" },
        @{ name = "Bamboo Cutting Board"; desc = "Large bamboo cutting board with juice groove"; tags = "kitchen,bamboo,board" },
        @{ name = "Scented Candle"; desc = "Soy wax scented candle with lavender fragrance 50h burn"; tags = "candle,scented,decor" },
        @{ name = "Wall Clock"; desc = "Minimalist 12-inch silent wall clock"; tags = "clock,wall,decor" },
        @{ name = "Shower Curtain"; desc = "Waterproof fabric shower curtain with hooks included"; tags = "bathroom,shower,curtain" },
        @{ name = "Throw Pillow"; desc = "Decorative square throw pillow cover with insert"; tags = "pillow,decor,cushion" },
        @{ name = "Garden Hose"; desc = "50ft expandable garden hose with 8 function spray nozzle"; tags = "garden,hose,water" },
        @{ name = "Cookware Set"; desc = "10-piece non-stick cookware set with glass lids"; tags = "kitchen,cookware,pots" },
        @{ name = "Bed Sheet Set"; desc = "Queen size 4-piece microfiber bed sheet set"; tags = "bedding,sheets,queen" },
        @{ name = "Knife Set"; desc = "14-piece stainless steel knife block set with sharpener"; tags = "kitchen,knives,stainless" },
        @{ name = "Area Rug"; desc = "5x7 ft soft area rug with non-slip backing"; tags = "rug,decor,floor" },
        @{ name = "Towel Set"; desc = "6-piece Egyptian cotton bath towel set"; tags = "bathroom,towels,cotton" },
        @{ name = "Watering Can"; desc = "1.5 gallon watering can with long spout for garden"; tags = "garden,watering,plant" },
        @{ name = "Picture Frame"; desc = "Set of 3 gallery wall picture frames 8x10 inch"; tags = "frame,decor,wall" },
        @{ name = "Laundry Basket"; desc = "Foldable laundry hamper with handles and lid"; tags = "laundry,basket,storage" },
        @{ name = "Desk Organizer"; desc = "Wooden desk organizer with phone stand and pen holder"; tags = "desk,organizer,office" },
        @{ name = "Air Purifier"; desc = "HEPA air purifier for rooms up to 300 sq ft"; tags = "air,purifier,hepa" }
    )
    "Sports" = @(
        @{ name = "Yoga Mat"; desc = "Extra thick non-slip yoga mat with carrying strap"; tags = "yoga,mat,fitness" },
        @{ name = "Dumbbell Set"; desc = "Adjustable dumbbell set 5-50 lbs with storage tray"; tags = "dumbbell,weights,fitness" },
        @{ name = "Resistance Bands"; desc = "Set of 5 progressive resistance bands with handles"; tags = "bands,resistance,fitness" },
        @{ name = "Jump Rope"; desc = "Speed jump rope with ball bearings and foam grips"; tags = "rope,cardio,fitness" },
        @{ name = "Tennis Racket"; desc = "Lightweight graphite tennis racket with overgrip"; tags = "tennis,racket,sports" },
        @{ name = "Basketball"; desc = "Official size 7 indoor outdoor basketball"; tags = "basketball,sports,ball" },
        @{ name = "Foam Roller"; desc = "High-density foam roller for muscle recovery and massage"; tags = "roller,foam,recovery" },
        @{ name = "Cycling Gloves"; desc = "Padded cycling gloves with breathable mesh and grip"; tags = "cycling,gloves,sports" },
        @{ name = "Running Belt"; desc = "Slim running waist belt for phone and keys"; tags = "running,belt,fitness" },
        @{ name = "Swim Goggles"; desc = "Anti-fog mirrored swim goggles with adjustable strap"; tags = "swim,goggles,water" },
        @{ name = "Kettlebell"; desc = "Cast iron kettlebell 20kg with smooth handle"; tags = "kettlebell,weights,fitness" },
        @{ name = "Soccer Ball"; desc = "Size 5 match quality soccer ball with butyl bladder"; tags = "soccer,ball,sports" },
        @{ name = "Pull Up Bar"; desc = "Doorway pull up bar with multiple grip positions"; tags = "pullup,bar,fitness" },
        @{ name = "Exercise Ball"; desc = "65cm anti-burst exercise ball with pump"; tags = "ball,exercise,fitness" },
        @{ name = "Hiking Backpack"; desc = "40L waterproof hiking backpack with rain cover"; tags = "hiking,backpack,outdoor" },
        @{ name = "Boxing Gloves"; desc = "16oz synthetic leather boxing gloves with wrist support"; tags = "boxing,gloves,sports" },
        @{ name = "Bicycle Pump"; desc = "Floor bike pump with pressure gauge and valve adapter"; tags = "bike,pump,cycling" },
        @{ name = "Fishing Rod"; desc = "6ft carbon fiber spinning fishing rod and reel combo"; tags = "fishing,rod,outdoor" },
        @{ name = "Camping Tent"; desc = "4-person waterproof dome tent with carry bag"; tags = "camping,tent,outdoor" },
        @{ name = "Gym Towel"; desc = "Quick-dry microfiber gym towel with hanging loop"; tags = "towel,gym,fitness" }
    )
    "Books" = @(
        @{ name = "Clean Code"; desc = "A handbook of agile software craftsmanship by Robert C Martin"; tags = "programming,software,clean" },
        @{ name = "Atomic Habits"; desc = "An easy and proven way to build good habits by James Clear"; tags = "self-help,habits,personal" },
        @{ name = "The Pragmatic Programmer"; desc = "Your journey to mastery by Andy Hunt and Dave Thomas"; tags = "programming,software,pragmatic" },
        @{ name = "Deep Work"; desc = "Rules for focused success in a distracted world by Cal Newport"; tags = "productivity,focus,work" },
        @{ name = "Design Patterns"; desc = "Elements of reusable object-oriented software by GoF"; tags = "programming,design,patterns" },
        @{ name = "Sapiens"; desc = "A brief history of humankind by Yuval Noah Harari"; tags = "history,humanity,science" },
        @{ name = "The Lean Startup"; desc = "How today's entrepreneurs use continuous innovation by Eric Ries"; tags = "business,startup,lean" },
        @{ name = "Thinking Fast and Slow"; desc = "The psychology of human decision making by Daniel Kahneman"; tags = "psychology,thinking,science" },
        @{ name = "Effective Java"; desc = "Best practices for the Java platform by Joshua Bloch"; tags = "java,programming,best-practices" },
        @{ name = "Refactoring"; desc = "Improving the design of existing code by Martin Fowler"; tags = "programming,refactoring,code" },
        @{ name = "The Alchemist"; desc = "A fable about following your dream by Paulo Coelho"; tags = "fiction,novel,philosophy" },
        @{ name = "Rich Dad Poor Dad"; desc = "What the rich teach their kids about money by Robert Kiyosaki"; tags = "finance,money,education" },
        @{ name = "1984"; desc = "Dystopian social science fiction novel by George Orwell"; tags = "fiction,dystopian,classic" },
        @{ name = "The Subtle Art"; desc = "A counterintuitive approach to living a good life by Mark Manson"; tags = "self-help,life,personal" },
        @{ name = "Educated"; desc = "A memoir by Tara Westover about transformation through education"; tags = "memoir,education,biography" },
        @{ name = "Start With Why"; desc = "How great leaders inspire everyone to take action by Simon Sinek"; tags = "leadership,business,motivation" },
        @{ name = "The Martian"; desc = "A science fiction novel about survival on Mars by Andy Weir"; tags = "fiction,scifi,space" },
        @{ name = "Hooked"; desc = "How to build habit-forming products by Nir Eyal"; tags = "business,product,psychology" },
        @{ name = "Eloquent JavaScript"; desc = "A modern introduction to programming by Marijn Haverbeke"; tags = "javascript,programming,web" },
        @{ name = "Gone Girl"; desc = "A psychological thriller novel by Gillian Flynn"; tags = "fiction,thriller,mystery" }
    )
}

# 5. Create 1000 products (10 batches x 100 templates with suffix)
Write-Host "`n=== Creating 1000 products ===" -ForegroundColor Cyan
$count = 0
$batches = 10

for ($batch = 1; $batch -le $batches; $batch++) {
    $suffix = if ($batch -eq 1) { "" } else { " v$batch" }

    foreach ($catName in $categoryIds.Keys) {
        $catId = $categoryIds[$catName]
        $products = $productTemplates[$catName]

        for ($i = 0; $i -lt $products.Count; $i++) {
            $p = $products[$i]
            $count++
            $seed = Get-Random -Minimum 1 -Maximum 100000
            $imageUrl = "https://picsum.photos/seed/$seed/400/400"

            $price = [math]::Round((Get-Random -Minimum 10 -Maximum 500) + (Get-Random -Minimum 0 -Maximum 99) / 100, 2)
            $discount = Get-Random -Minimum 0 -Maximum 30
            $specialPrice = [math]::Round($price - ($price * $discount / 100), 2)
            $quantity = Get-Random -Minimum 5 -Maximum 200

            $body = @{
                productName  = "$($p.name)$suffix"
                description  = $p.desc
                tags         = $p.tags
                image        = $imageUrl
                quantity     = $quantity
                price        = $price
                discount     = $discount
                specialPrice = $specialPrice
            } | ConvertTo-Json

            try {
                $resp = Invoke-PostWithCsrf -Uri "$baseUrl/api/admin/categories/$catId/product" -Body $body
                if ($count % 50 -eq 0) { Write-Host "  [$count] $($p.name)$suffix -> cat=$catName" -ForegroundColor Green }
            } catch {
                Write-Host "  [$count] FAILED: $($p.name)$suffix - $($_.Exception.Message)" -ForegroundColor Red
            }
        }
    }
    Write-Host "  --- Batch $batch/$batches complete ($count products) ---" -ForegroundColor Yellow
}

Write-Host "`n=== Done! Created $count products across $($categoryIds.Count) categories ===" -ForegroundColor Cyan
Write-Host "Frontend: http://localhost:5173" -ForegroundColor Yellow
Write-Host "Swagger:  http://localhost:8080/swagger-ui/index.html" -ForegroundColor Yellow