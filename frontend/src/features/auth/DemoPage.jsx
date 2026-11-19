import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate } from 'react-router';
import { demoLogin, getDemo } from '../../api/demo';
import { keys } from '../../api/queryClient';
import { useAuth } from '../../auth/AuthContext';
import { FormAlert } from '../../components/Fields';
import { FoodPicture } from '../../components/FoodPicture';
import { LogoMark } from '../../components/Logo';
import { LoadingScreen } from '../../components/StatePanels';
import { usePageTitle } from '../../hooks/usePageTitle';
import { AuthCard } from './AuthCard';

const ROLE_TEXT = {
  DONOR: { title: 'Log in as Donor', text: 'A verified bakery: list food and hand it over' },
  RECEIVER: { title: 'Log in as Receiver', text: 'Find food nearby, claim it and collect it' },
  ADMIN: { title: 'Log in as Admin', text: 'Verify donors, reports, accounts' },
};

function RolePicture({ role }) {
  if (role === 'ADMIN') {
    return <LogoMark size={34} />;
  }
  return <FoodPicture category={role === 'DONOR' ? 'BAKERY' : 'COOKED_MEAL'} width={40} />;
}

/**
 * /demo, only in demo mode (DEMO_MODE=true): one button per role. The browser sends only the role; the backend
 * logs in its demo account, so the demo password never reaches the page.
 */
export function DemoPage() {
  usePageTitle('Try FoodLoop');
  const { signIn } = useAuth();
  const [problem, setProblem] = useState(null);
  const [busyRole, setBusyRole] = useState(null);
  const demo = useQuery({ queryKey: keys.demo, queryFn: getDemo, retry: false, staleTime: Infinity });

  if (demo.isPending) {
    return <LoadingScreen />;
  }
  if (demo.isError) {
    // Demo mode is off (404): there is no demo page.
    return <Navigate to="/" replace />;
  }

  async function logInAs(role) {
    setProblem(null);
    setBusyRole(role);
    try {
      signIn(await demoLogin(role));
    } catch (error) {
      setProblem(error.message);
      setBusyRole(null);
    }
  }

  return (
    <AuthCard>
      <h1 className="text-[22px]">Try FoodLoop</h1>
      <p className="muted sm mt-1">Demo data in Bengaluru. Each button logs you in as that role.</p>
      <div className="demo-roles">
        {demo.data.roles.map(({ role }) => (
          <button key={role} type="button" onClick={() => logInAs(role)} disabled={busyRole !== null}>
            <RolePicture role={role} />
            <span>
              <b>{busyRole === role ? 'Logging in…' : ROLE_TEXT[role].title}</b>
              <span className="muted xs">{ROLE_TEXT[role].text}</span>
            </span>
          </button>
        ))}
      </div>
      <FormAlert message={problem} />
      <p className="muted xs">
        Demo accounts can't change their email or password, or be deleted. <Link to="/">Back to Log in</Link>
      </p>
    </AuthCard>
  );
}
