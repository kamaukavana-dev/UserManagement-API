import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const EMAIL = `smoke_${Math.floor(Math.random()*1e6)}@example.com`;
const PASSWORD = 'SmokeTest@123';

export default function () {
  // Register
  const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    firstName: 'Smoke',
    lastName: 'Test',
    email: EMAIL,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });

  check(registerRes, {
    'register status 201/409': (r) => r.status === 201 || r.status === 409,
  });

  // Login
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    email: EMAIL,
    password: PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });

  check(loginRes, {
    'login status 200': (r) => r.status === 200,
    'token present': (r) => r.json('accessToken') !== undefined,
  });

  sleep(1);
}
