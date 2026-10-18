import express from 'express';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const app = express();
const port = 3000;

app.use(express.json());

const MONETIZATION_FILE = join(__dirname, 'monetization.json');

// Default monetization settings & metrics
function getMonetizationData() {
  try {
    if (fs.existsSync(MONETIZATION_FILE)) {
      return JSON.parse(fs.readFileSync(MONETIZATION_FILE, 'utf-8'));
    }
  } catch (e) {
    console.error('Error reading monetization data:', e);
  }
  return {
    config: {
      paypalEmail: '',
      paypalMeLink: '',
      stripePaymentLink: '',
      kofiLink: '',
      mercadopagoLink: '',
      adsensePublisherId: '',
      admobBannerId: '',
      customSponsorUrl: 'https://ais-pre-wzzw5d5ftibpzkacvniwcg-638095220417.us-west2.run.app',
      customSponsorText: '¡Descubre las novedades de Arcano Royale!',
      sandboxMode: true,
      currency: 'USD',
      usdToCopRate: 4100
    },
    metrics: {
      totalRevenueUSD: 0,
      activeRevenueUSD: 0,
      passiveRevenueUSD: 0,
      purchasesCount: 0,
      adImpressions: 0,
      rewardedAdViews: 0,
      transactions: []
    }
  };
}

function saveMonetizationData(data) {
  try {
    fs.writeFileSync(MONETIZATION_FILE, JSON.stringify(data, null, 2), 'utf-8');
  } catch (e) {
    console.error('Error saving monetization data:', e);
  }
}

// Monetization API routes
app.get('/api/monetization', (req, res) => {
  res.json(getMonetizationData());
});

app.post('/api/monetization/config', (req, res) => {
  const data = getMonetizationData();
  data.config = { ...data.config, ...(req.body || {}) };
  saveMonetizationData(data);
  res.json({ success: true, config: data.config });
});

// Record ad impressions or rewarded views (passive income calculation based on eCPM)
app.post('/api/monetization/record-ad', (req, res) => {
  const { type } = req.body || {}; // 'banner' | 'rewarded' | 'interstitial'
  const data = getMonetizationData();
  
  // Standard web gaming eCPMs: Rewarded Video ~ $8.00 / 1000 views, Banner ~ $0.80 / 1000 views
  const estimatedEarn = type === 'rewarded' ? 0.008 : 0.0008;
  
  data.metrics.adImpressions = (data.metrics.adImpressions || 0) + 1;
  if (type === 'rewarded') {
    data.metrics.rewardedAdViews = (data.metrics.rewardedAdViews || 0) + 1;
  }
  data.metrics.passiveRevenueUSD = Number(((data.metrics.passiveRevenueUSD || 0) + estimatedEarn).toFixed(4));
  data.metrics.totalRevenueUSD = Number((data.metrics.activeRevenueUSD + data.metrics.passiveRevenueUSD).toFixed(4));
  
  saveMonetizationData(data);
  res.json({ success: true, metrics: data.metrics });
});

// Process/simulate a real-world active purchase
app.post('/api/monetization/purchase', (req, res) => {
  const { productId, productName, amountUSD, customerName, paymentMethod } = req.body || {};
  const data = getMonetizationData();
  
  const price = Number(amountUSD) || 1.99;
  data.metrics.purchasesCount = (data.metrics.purchasesCount || 0) + 1;
  data.metrics.activeRevenueUSD = Number(((data.metrics.activeRevenueUSD || 0) + price).toFixed(2));
  data.metrics.totalRevenueUSD = Number((data.metrics.activeRevenueUSD + data.metrics.passiveRevenueUSD).toFixed(2));
  
  const tx = {
    id: 'TX-' + Date.now().toString(36).toUpperCase(),
    date: new Date().toISOString(),
    productId: productId || 'vip_pass',
    productName: productName || 'Pase Arcano VIP',
    amountUSD: price,
    customerName: customerName || 'Hechicero Anónimo',
    paymentMethod: paymentMethod || (data.config.sandboxMode ? 'Sandbox (Test)' : 'Stripe / PayPal')
  };
  
  data.metrics.transactions.unshift(tx);
  if (data.metrics.transactions.length > 50) data.metrics.transactions.pop();
  
  saveMonetizationData(data);
  res.json({ success: true, transaction: tx, metrics: data.metrics });
});

// Reset metrics if developer wants
app.post('/api/monetization/reset-metrics', (req, res) => {
  const data = getMonetizationData();
  data.metrics = {
    totalRevenueUSD: 0,
    activeRevenueUSD: 0,
    passiveRevenueUSD: 0,
    purchasesCount: 0,
    adImpressions: 0,
    rewardedAdViews: 0,
    transactions: []
  };
  saveMonetizationData(data);
  res.json({ success: true, metrics: data.metrics });
});

// Serve static assets from www/ and root
app.use(express.static(join(__dirname, 'www')));
app.use(express.static(__dirname));

// Catch-all route to serve index.html for SPA (compatible with Express 5)
app.use((req, res) => {
  res.sendFile(join(__dirname, 'www', 'index.html'));
});

app.listen(port, '0.0.0.0', () => {
  console.log(`Arcano Royale server running at http://0.0.0.0:${port}`);
});

