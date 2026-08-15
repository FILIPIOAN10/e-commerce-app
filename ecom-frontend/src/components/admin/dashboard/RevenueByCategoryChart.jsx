import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

const RevenueByCategoryChart = ({ data }) => {
    if (!data || data.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 text-gray-400">
                No revenue data available
            </div>
        );
    }

    const chartData = data.map(item => ({
        name: item.label.length > 15 ? item.label.substring(0, 15) + '...' : item.label,
        fullName: item.label,
        revenue: Math.round(item.value * 100) / 100,
    }));

    return (
        <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData} layout="vertical" margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
                <XAxis type="number" tick={{ fontSize: 12 }} />
                <YAxis dataKey="name" type="category" tick={{ fontSize: 11 }} width={120} />
                <Tooltip
                    formatter={(value, name, props) => [`$${value}`, props.payload.fullName]}
                    contentStyle={{ borderRadius: '8px', border: '1px solid #e0e0e0' }}
                />
                <Legend />
                <Bar
                    dataKey="revenue"
                    fill="#1976d2"
                    radius={[0, 4, 4, 0]}
                    name="Revenue ($)"
                />
            </BarChart>
        </ResponsiveContainer>
    );
};

export default RevenueByCategoryChart;
